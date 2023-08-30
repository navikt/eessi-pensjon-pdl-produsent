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
import javax.annotation.PostConstruct

@Service
class SedListenerAdresse(
    private val sedHendelseBehandler: SedHendelseBehandler,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
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

                val offsetToSkip = listOf(563319L, 563302L, 386664L, 386665L, 386666L, 437852L, 437861L, 521570L, 529077L, 530286L, 532780L, 533008L, 534633L, 534634L, 535434L, 541905L, 542460L, 542499L, 608449L, 613092L, 656516L, 661893L)
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
