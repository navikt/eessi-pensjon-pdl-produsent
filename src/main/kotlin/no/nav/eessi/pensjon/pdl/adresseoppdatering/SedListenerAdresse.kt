package no.nav.eessi.pensjon.pdl.adresseoppdatering

import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.SedHendelse
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import no.nav.eessi.pensjon.pdl.adresseoppdatering.Adresseoppdatering.Error
import no.nav.eessi.pensjon.pdl.adresseoppdatering.Adresseoppdatering.NoUpdate
import no.nav.eessi.pensjon.pdl.adresseoppdatering.Adresseoppdatering.Update
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
import java.util.concurrent.*
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
                    logger.debug("hendelse mottatt: $hendelse")

                    val sedHendelse = SedHendelse.fromJson(hendelse)
                    if (profile == "prod" && sedHendelse.avsenderId in listOf("NO:NAVAT05", "NO:NAVAT07")) {
                        logger.error("Avsender id er ${sedHendelse.avsenderId}. Dette er testdata i produksjon!!!\n$sedHendelse")
                        acknowledgment.acknowledge()
                        return@measure
                    }

                    val result = adresseoppdatering.oppdaterUtenlandskKontaktadresse(sedHendelse)

                    when(result) {
                        is NoUpdate -> logger.info(result.toString())
                        is Update ->  {
                            personMottakKlient.opprettPersonopplysning(result.pdlEndringsOpplysninger)
                            logger.info(result.toString())
                        }
                        is Error -> logger.error(result.toString())
                    }

                    acknowledgment.acknowledge()

                } catch (ex: Exception) {
                    logger.error("Noe gikk galt under behandling av SED-hendelse for adresse:\n $hendelse \n", ex)
                    throw ex
                }
            }
            latch.countDown()
        }
    }

}