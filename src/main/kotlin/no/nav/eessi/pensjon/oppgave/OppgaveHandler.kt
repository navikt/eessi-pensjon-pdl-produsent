package no.nav.eessi.pensjon.oppgave

import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.lagring.LagringsService
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingRequest
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingService
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPersonPDL
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import javax.annotation.PostConstruct

private const val LAGRING_IDENT = "_IDENT"
private const val LAGRING_GJENLEV = "_GJENLEV"

@Service
class OppgaveHandler(
    private val oppgaveKafkaTemplate: KafkaTemplate<String, String>,
    private val lagringsService: LagringsService,
    private val oppgaveruting: OppgaveRoutingService,
    @Value("\${namespace}") var nameSpace: String,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest() ) : OppgaveOppslag {

    private val logger = LoggerFactory.getLogger(OppgaveHandler::class.java)
    private val X_REQUEST_ID = "x_request_id"

    private lateinit var publiserOppgavemelding: MetricsHelper.Metric
    private lateinit var oppgaveForUid: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        publiserOppgavemelding = metricsHelper.init("publiserOppgavemelding")
        oppgaveForUid = metricsHelper.init("OppgaveForUid")
    }

    fun opprettOppgave(oppgaveData: OppgaveData): Boolean {
        return if(oppgaveData is OppgaveDataUID){
            opprettOppgave(oppgaveData.sedHendelse, oppgaveData.identifisertPerson, LAGRING_IDENT)
        } else {
            opprettOppgave(oppgaveData.sedHendelse, oppgaveData.identifisertPerson, LAGRING_GJENLEV)
        }
    }

    private fun opprettOppgave(sedHendelse: SedHendelse, identifisertePerson: IdentifisertPersonPDL, lagringsPathPostfix: String): Boolean {
        return oppgaveForUid.measure {
            return@measure if (!finnesOppgavenAllerede(sedHendelse.rinaSakId.plus(lagringsPathPostfix))) {
                val melding = OppgaveMelding(
                    aktoerId = identifisertePerson.aktoerId,
                    filnavn = null,
                    sedType = null,
                    tildeltEnhetsnr = opprettOppgaveRuting(sedHendelse, identifisertePerson),
                    rinaSakId = sedHendelse.rinaSakId,
                    hendelseType = HendelseType.MOTTATT,
                    oppgaveType = OppgaveType.PDL
                )

                opprettOppgaveMeldingPaaKafkaTopic(melding)
                lagringsService.lagreHendelseMedSakId(sedHendelse.rinaSakId.plus(lagringsPathPostfix))
                logger.info("Opprett oppgave og lagret til s3")
                true
            } else {
                logger.info("Hendelse finnes fra før i bucket. Oppgave blir derfor ikke opprettet")
                false
            }
        }
    }

    override fun finnesOppgavenAllerede(rinaSakId: String) = !lagringsService.kanHendelsenOpprettes(rinaSakId)
    override fun finnesOppgavenAlleredeForUID(rinaSakId: String) = !lagringsService.kanHendelsenOpprettes(rinaSakId.plus(LAGRING_IDENT))
    override fun finnesOppgavenAlleredeGJENLEV(rinaSakId: String) = !lagringsService.kanHendelsenOpprettes(rinaSakId.plus(LAGRING_GJENLEV))

    private fun opprettOppgaveRuting(sedHendelse: SedHendelse, identifisertePerson : IdentifisertPersonPDL) : Enhet {
        return oppgaveruting.route(OppgaveRoutingRequest.fra(
            identifisertePerson,
            identifisertePerson.fnr!!.getBirthDate(),
            identifisertePerson.personRelasjon?.saktype,
            sedHendelse,
            HendelseType.MOTTATT,
            null,
            identifisertePerson.harAdressebeskyttelse
        ))
    }

    private fun opprettOppgaveMeldingPaaKafkaTopic(melding: OppgaveMelding) {
        val key = MDC.get(X_REQUEST_ID)
        val payload = melding.toJson()

        publiserOppgavemelding.measure {
            logger.info("Opprette oppgave melding på kafka: ${oppgaveKafkaTemplate.defaultTopic}  melding: $melding")
            oppgaveKafkaTemplate.sendDefault(key, payload).get()
        }
    }
}

interface OppgaveOppslag {
    fun finnesOppgavenAllerede(rinaSakId: String): Boolean
    fun finnesOppgavenAlleredeForUID(rinaSakId: String): Boolean
    fun finnesOppgavenAlleredeGJENLEV(rinaSakId: String): Boolean
}

interface OppgaveData {
    val sedHendelse: SedHendelse
    val identifisertPerson: IdentifisertPersonPDL
}

data class OppgaveDataUID(
    override val sedHendelse: SedHendelse,
    override val identifisertPerson: IdentifisertPersonPDL
) : OppgaveData

data class OppgaveDataGjenlevUID(
    override val sedHendelse: SedHendelse,
    override val identifisertPerson: IdentifisertPersonPDL
) : OppgaveData