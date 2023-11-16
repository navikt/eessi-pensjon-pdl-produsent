package no.nav.eessi.pensjon.pdl.adresseoppdatering

import no.nav.eessi.pensjon.metrics.MetricsHelper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import java.util.*
import java.util.concurrent.CountDownLatch

@Service
class SedListenerAdresse(
    private val sedHendelseBehandler: SedHendelseBehandler,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {
    val latch: CountDownLatch = CountDownLatch(1)
    private val logger = LoggerFactory.getLogger(SedListenerAdresse::class.java)
    private val secureLogger = LoggerFactory.getLogger("secureLog")

    private var adresseMetric: MetricsHelper.Metric = metricsHelper.init("consumeIncomingSedForAddress")

    @KafkaListener(
        containerFactory = "sedKafkaListenerContainerFactory",
        topics = ["\${kafka.utenlandskAdresse.topic}"],
        groupId = "\${kafka.utenlandskAdresse.groupid}"
    )
    fun consumeSedMottatt(hendelse: String, cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        MDC.putCloseable("x_request_id", UUID.randomUUID().toString()).use {
            adresseMetric.measure {
                logger.info("SED-hendelse mottatt i partisjon: ${cr.partition()}, med offset: ${cr.offset()} ")

                val offsetToSkip = listOf(742867L, 760575L, 766000L, 794071L, 795091L)
                if (cr.offset() in offsetToSkip) {
                    logger.warn("Hopper over offset: ${cr.offset()}")
                }
                else {
                    try {
                        sedHendelseBehandler.behandle(hendelse)
                        logger.info("Acket sedMottatt melding med offset: ${cr.offset()} i partisjon ${cr.partition()}")
                        latch.countDown()
                    } catch (ex: HttpClientErrorException) {
                        if (ex.statusCode == HttpStatus.LOCKED)
                            logger.error("Det pågår allerede en adresseoppdatering på bruker", ex)
                        else throw ex
                    } catch (ex: Exception) {
                        logger.error("Noe gikk galt under behandling av SED-hendelse for adresse", ex)
                        secureLogger.info("Noe gikk galt under behandling av SED-hendelse for adresse:\n$hendelse")
                        throw ex
                    }
                }
                acknowledgment.acknowledge()
            }
        }
    }
}
