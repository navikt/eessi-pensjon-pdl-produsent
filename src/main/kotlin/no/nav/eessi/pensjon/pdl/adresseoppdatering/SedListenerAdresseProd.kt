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

// TODO Dette er en temporær versjon - kun for å se hva som evt vil skrives til PDL
@Profile("prod") // Feature toggle -- OBS! IKKE ENDRE UTEN Å HA TENKT PÅ OM KAFKA SKAL KONSUMERE FRA START! MEN: FORELØPIG LOGGER VI BARE SÅ DET ER OK!!
@Service
class SedListenerAdresseProd(
    private val adresseoppdatering: Adresseoppdatering,
    private val personMottakKlient: PersonMottakKlient,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest(),
    @Value("\${SPRING_PROFILES_ACTIVE:}") private val profile: String
) {
    val latch: CountDownLatch = CountDownLatch(1)
    private val logger = LoggerFactory.getLogger(SedListenerAdresseProd::class.java)
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

                try {
                    secureLogger.debug("Hendelse mottatt:\n$hendelse")

                    val sedHendelse = SedHendelse.fromJson(hendelse)
                    if (profile == "prod" && sedHendelse.avsenderId in listOf("NO:NAVAT05", "NO:NAVAT07")) {
                        logger.error("Avsender id er ${sedHendelse.avsenderId}. Dette er testdata i produksjon!!!\n$sedHendelse")
                        acknowledgment.acknowledge()
                        return@measure
                    }

                    when(val result = adresseoppdatering.oppdaterUtenlandskKontaktadresse(sedHendelse)) {
                        is NoUpdate -> logger.info(result.toString())
                        is Update -> {
                            // UTKOMMENTERT INNTIL VIDERE
                            // personMottakKlient.opprettPersonopplysning(result.pdlEndringsOpplysninger)
                            // logger.info("Update - oppdatering levert til PDL.")
                            logger.info("Denne hendelsen ville ført til en oppdatering til PDL (foreløpig feature-toggled).")
                            secureLogger.info("Update til PDL:\n${result.toJson()}")
                        }
                    }

                    acknowledgment.acknowledge()

                } catch (ex: Exception) {
                    logger.error("Noe gikk galt under behandling av SED-hendelse for adresse:\n$hendelse\n", ex)
                    throw ex
                }
            }
            latch.countDown()
        }
    }

}