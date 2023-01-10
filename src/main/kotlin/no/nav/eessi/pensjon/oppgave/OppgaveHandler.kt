package no.nav.eessi.pensjon.oppgave

import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.lagring.LagringsService
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingRequest
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingService
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import javax.annotation.PostConstruct

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

    fun opprettOppgaveForUid(oppgaveData: OppgaveData) =
        opprettOppgaveForUid(oppgaveData.sedHendelse, oppgaveData.identifisertPerson)

    private fun opprettOppgaveForUid(sedHendelse: SedHendelse, identifisertePerson: IdentifisertPerson): Boolean {
        return oppgaveForUid.measure {
            return@measure if (!finnesOppgavenAllerede(sedHendelse.rinaSakId)) {
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
                lagringsService.lagreHendelseMedSakId(sedHendelse.rinaSakId)
                logger.info("Opprett oppgave og lagret til s3")
                true
            } else {
                logger.info("Hendelse finnes fra før i bucket. Oppgave blir derfor ikke opprettet")
                false
            }
        }
    }

    override fun finnesOppgavenAllerede(rinaSakId: String) = !lagringsService.kanHendelsenOpprettes(rinaSakId)

    private fun opprettOppgaveRuting(sedHendelse: SedHendelse, identifisertePerson : IdentifisertPerson) : Enhet {
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
}

data class OppgaveData(
    val sedHendelse: SedHendelse,
    val identifisertPerson: IdentifisertPerson
)