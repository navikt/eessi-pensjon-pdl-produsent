package no.nav.eessi.pensjon.handler

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.eux.UtenlandskId
import no.nav.eessi.pensjon.lagring.LagringsService
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SedHendelseModel
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
    private val aivenOppgaveKafkaTemplate: KafkaTemplate<String, String>,
    private val lagringsService: LagringsService,
    private val oppgaveruting: OppgaveRoutingService,
    @Value("\${namespace}") var nameSpace: String,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry()) ) {

    private val logger = LoggerFactory.getLogger(OppgaveHandler::class.java)
    private val X_REQUEST_ID = "x_request_id"

    private lateinit var publiserOppgavemelding: MetricsHelper.Metric
    private lateinit var oppgaveForUid: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        publiserOppgavemelding = metricsHelper.init("publiserOppgavemelding")
        oppgaveForUid = metricsHelper.init("OppgaveForUid")
    }

    fun opprettOppgaveForUid(hendelseModel: SedHendelseModel, utenlandskIdSed: UtenlandskId, identifisertePerson : IdentifisertPerson): Boolean {
        if(nameSpace == "p") {
            logger.warn("OppgaveHandler ikke klar for PROD ennå")
            return false
        }

        return oppgaveForUid.measure {
            return@measure if (lagringsService.kanHendelsenOpprettes(hendelseModel)) {
                val melding = OppgaveMelding(
                    aktoerId = identifisertePerson.aktoerId,
                    filnavn = null,
                    sedType = null,
                    tildeltEnhetsnr = opprettOppgaveRuting(hendelseModel, identifisertePerson),
                    rinaSakId = hendelseModel.rinaSakId,
                    hendelseType = HendelseType.MOTTATT,
                    oppgaveType = OppgaveType.PDL
                )

                opprettOppgaveMeldingPaaKafkaTopic(melding)
                lagringsService.lagreHendelseMedSakId(hendelseModel)
                logger.info("Opprett oppgave og lagret til s3")
                true
            } else {
                logger.info("Finnes fra før, gjør ingenting. .. ")
                false
            }
        }
    }

    private fun opprettOppgaveRuting(hendelseModel: SedHendelseModel, identifisertePerson : IdentifisertPerson) : Enhet {
        return oppgaveruting.route(OppgaveRoutingRequest.fra(
            identifisertePerson,
            identifisertePerson.fnr!!.getBirthDate(),
            identifisertePerson.personRelasjon.saktype,
            hendelseModel,
            HendelseType.MOTTATT,
            null,
            identifisertePerson.harAdressebeskyttelse
        ))
    }

    private fun opprettOppgaveMeldingPaaKafkaTopic(melding: OppgaveMelding) {
        val key = MDC.get(X_REQUEST_ID)
        val payload = melding.toJson()

        publiserOppgavemelding.measure {
            logger.info("Opprette oppgave melding på kafka: ${aivenOppgaveKafkaTemplate.defaultTopic}  melding: $melding")
            aivenOppgaveKafkaTemplate.sendDefault(key, payload).get()
        }
    }
}
