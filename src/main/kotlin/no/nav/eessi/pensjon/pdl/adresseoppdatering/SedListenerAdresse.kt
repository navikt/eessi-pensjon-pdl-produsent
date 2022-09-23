package no.nav.eessi.pensjon.pdl.adresseoppdatering

import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.SedHendelse
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import no.nav.eessi.pensjon.pdl.adresseoppdatering.Adresseoppdatering.NoUpdate
import no.nav.eessi.pensjon.pdl.adresseoppdatering.Adresseoppdatering.Update
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

@Profile("!prod") // Feature toggle -- OBS! IKKE ENDRE UTEN Å HA TENKT PÅ OM KAFKA SKAL KONSUMERE FRA START!
@Service
class SedListenerAdresse(
    private val adresseoppdatering: Adresseoppdatering,
    private val personMottakKlient: PersonMottakKlient,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest(),
    @Value("\${SPRING_PROFILES_ACTIVE:}") private val profile: String
) {
    val latch: CountDownLatch = CountDownLatch(1)
    private val logger = LoggerFactory.getLogger(SedListenerAdresse::class.java)
    private val secureLogger = LoggerFactory.getLogger("secureLog")

    private lateinit var adresseMetric: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        adresseMetric = metricsHelper.init("consumeIncomingSedForAddress")
    }

    @KafkaListener(
        containerFactory = "sedKafkaListenerContainerFactory",
        topics = ["\${kafka.utenlandskAdresse.topic}"],
        groupId = "\${kafka.utenlandskAdresse.groupid}"
    )
    fun consumeSedMottatt(hendelse: String, cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        MDC.putCloseable("x_request_id", UUID.randomUUID().toString()).use {
            adresseMetric.measure {
                logger.info("SED-hendelse mottatt i partisjon: ${cr.partition()}, med offset: ${cr.offset()} ")
                secureLogger.debug("Hendelse mottatt:\n$hendelse")

                try {
                    behandle(hendelse)
                    acknowledgment.acknowledge()
                    logger.info("Acket sedMottatt melding med offset: ${cr.offset()} i partisjon ${cr.partition()}")
                    latch.countDown()
                } catch (ex: Exception) {
                    logger.error("Noe gikk galt under behandling av SED-hendelse for adresse:\n$hendelse\n", ex)
                    throw ex
                }
            }
        }
    }

    private fun behandle(hendelse: String) {
        val sedHendelse = SedHendelse.fromJson(hendelse)

        if (testDataInProd(sedHendelse)) {
            logger.error("Avsender id er ${sedHendelse.avsenderId}. Dette er testdata i produksjon!!!\n$sedHendelse")
            return
        }

        logger.info("*** Starter pdl endringsmelding (ADRESSE) prosess for BucType: ${sedHendelse.bucType}, SED: ${sedHendelse.sedType}, RinaSakID: ${sedHendelse.rinaSakId} ***")

        when (val result = adresseoppdatering.oppdaterUtenlandskKontaktadresse(sedHendelse)) {
            is NoUpdate -> logger.info(result.toString())
            is Update -> {
                personMottakKlient.opprettPersonopplysning(result.pdlEndringsOpplysninger)
                logger.info("Update - oppdatering levert til PDL.")
                secureLogger.info("Update til PDL:\n${result.toJson()}")
            }
        }
    }

    private fun testDataInProd(sedHendelse: SedHendelse) =
        profile == "prod" && sedHendelse.avsenderId in listOf("NO:NAVAT05", "NO:NAVAT07")

}