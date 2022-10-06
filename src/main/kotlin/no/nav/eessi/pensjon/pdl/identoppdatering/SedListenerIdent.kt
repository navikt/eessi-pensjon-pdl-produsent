package no.nav.eessi.pensjon.pdl.identoppdatering

import io.micrometer.core.instrument.Metrics
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.SedHendelse
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import no.nav.eessi.pensjon.pdl.identoppdatering.IdentOppdatering2.NoUpdate
import no.nav.eessi.pensjon.pdl.identoppdatering.IdentOppdatering2.Update
import no.nav.eessi.pensjon.utils.toJson
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.CountDownLatch
import javax.annotation.PostConstruct

@Profile("!prod") // Stoppet inntil videre i prod
@Service
class SedListenerIdent(
    private val personMottakKlient: PersonMottakKlient,
    private val identOppdatering: IdentOppdatering2,
    private val oldIdent: IdentOppdatering,
    @Value("\${SPRING_PROFILES_ACTIVE:}") private val profile: String,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {

    private val logger = LoggerFactory.getLogger(SedListenerIdent::class.java)
    private val secureLogger = LoggerFactory.getLogger("secureLog")
    private val latch = CountDownLatch(1)
    private lateinit var consumeIncomingSed: MetricsHelper.Metric

    fun getLatch() = latch

    @PostConstruct
    fun initMetrics() {
        consumeIncomingSed = metricsHelper.init("consumeIncomingSed")
    }

    @KafkaListener(
        containerFactory = "sedKafkaListenerContainerFactory",
        topics = ["\${kafka.sedMottatt.topic}"],
        groupId = "\${kafka.sedMottatt.groupid}"
    )
    fun consumeSedMottatt(hendelse: String, cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        MDC.putCloseable("x_request_id", UUID.randomUUID().toString()).use {
            consumeIncomingSed.measure {
                logger.info("SedMottatt i partisjon: ${cr.partition()}, med offset: ${cr.offset()}")
                try {
                    behandle(hendelse)
                    acknowledgment.acknowledge()
                    logger.info("Acket sedMottatt melding med offset: ${cr.offset()} i partisjon ${cr.partition()}")
                    latch.countDown()
                } catch (ex: Exception) {
                    logger.error("Noe gikk galt under behandling av SED-hendelse:\n $hendelse \n", ex)
                    throw ex
                }
            }
        }
    }

    private fun behandle(hendelse: String) {
        logger.debug(hendelse)
        logger.debug("Profile: $profile")
        val sedHendelse = sedHendelseMapping(hendelse).also { secureLogger.debug("Sedhendelse:\n${it.toJson()}") }

        if (testHendelseIProd(sedHendelse)) {
            logger.error("Avsender id er ${sedHendelse.avsenderId}. Dette er testdata i produksjon!!!\n$sedHendelse")
            return
        }

        logger.info("*** Starter pdl endringsmelding (IDENT) prosess for BucType: ${sedHendelse.bucType}, SED: ${sedHendelse.sedType}, RinaSakID: ${sedHendelse.rinaSakId} ***")

        val result = identOppdatering.oppdaterUtenlandskIdent(sedHendelse)
        val resultFraOldIdent = oldIdent.oppdaterUtenlandskIdent(sedHendelse)

        if(resultFraOldIdent.equals(result).not()){
            logger.debug("Gammel implementasjon: ${resultFraOldIdent.toJson()}\n")
            logger.debug("Ny implementasjon: ${result.toJson()}")
        }

        if (result is Update) {
            personMottakKlient.opprettPersonopplysning(result.pdlEndringsOpplysninger)
        }

        log(result)
        count(result.metricTagValue)
    }

    private fun log(result: IdentOppdatering2.Result) {
        when (result) {
            is Update -> {
                secureLogger.debug("Update:\n${result.toJson()}")
                logger.info("Update(${result.description}")
            }

            is NoUpdate -> {
                logger.info(result.toString())
            }
        }
    }

    private fun testHendelseIProd(sedHendelse: SedHendelse) =
        profile == "prod" && sedHendelse.avsenderId in listOf("NO:NAVAT05", "NO:NAVAT07")

    fun count(melding: String) {
        try {
            Metrics.counter("PDLIdentOppdateringResultat",   "melding", melding).increment()
        } catch (e: Exception) {
            logger.warn("Metrics feilet på enhet: $melding")
        }
    }

    fun sedHendelseMapping(hendelse: String): SedHendelse {
        val sedHendelseTemp = SedHendelse.fromJson(hendelse)

        //støtte avsenderland SE i testmiljø Q2
        return if (profile != "prod" && profile != "integrationtest") {
            sedHendelseTemp.copy(avsenderLand = "SE", avsenderNavn = "SE:test")
        } else {
            sedHendelseTemp
        }
    }
}
