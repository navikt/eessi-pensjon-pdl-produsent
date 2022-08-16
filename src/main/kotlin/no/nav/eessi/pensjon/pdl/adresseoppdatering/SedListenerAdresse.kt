package no.nav.eessi.pensjon.pdl.adresseoppdatering

import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.SedHendelseModel
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.CountDownLatch
import javax.annotation.PostConstruct

@Profile("!prod") // Feature toggle
@Service
class SedListenerAdresse(
    private val adresseoppdatering: Adresseoppdatering,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
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
                logger.debug("sed-hendelse for vurdering av adressemelding mot PDL i partisjon: ${cr.partition()}, med offset: ${cr.offset()} ")

                try {
                    val sedHendelse = SedHendelseModel.fromJson(hendelse)
                    if (adresseoppdatering.oppdaterUtenlandskKontaktadresse(sedHendelse)) {
                        // Gjorde oppdatering
                    } else {
                        // Gjorde ikke oppdatering?
                    }

                } catch (ex: Exception) {
                    logger.error("Noe gikk galt under behandling av SED-hendelse for adresse:\n $hendelse \n", ex)
                }
                acknowledgment.acknowledge()
                latch.countDown()
            }
        }
    }

}