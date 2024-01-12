package no.nav.eessi.pensjon.pdl.identoppdatering

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
class SedListenerIdent(
    private val behandleIdentHendelse: SedHendelseIdentBehandler,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {

    private val logger = LoggerFactory.getLogger(SedListenerIdent::class.java)
    private val latch = CountDownLatch(1)
    private var consumeIncomingSed: MetricsHelper.Metric = metricsHelper.init("consumeIncomingSed")
    private val secureLogger = LoggerFactory.getLogger("secureLog")

    fun getLatch() = latch

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
                    if (cr.offset() in listOf(518691L, 519143L, 768200L, 814710L, 840165L, 847199L, 887164L, 902181L)) {
                        logger.warn("Hopper over offset: ${cr.offset()} grunnet feil ved henting av vedlegg...")
                    } else {
                        behandleIdentHendelse.behandle(hendelse)
                    }
                    logger.info("Acket sedMottatt melding med offset: ${cr.offset()} i partisjon ${cr.partition()}")
                    latch.countDown()
                } catch (ex: HttpClientErrorException) {
                    if (ex.statusCode == HttpStatus.LOCKED)
                        logger.error("Det pågår allerede en adresseoppdatering på bruker", ex)
                    else throw ex
                } catch (ex: Exception) {
                    logger.error("Noe gikk galt under behandling av SED-hendelse", ex)
                    secureLogger.info("Noe gikk galt under behandling av SED-hendelse:\n$hendelse")
                    throw ex
                }
                acknowledgment.acknowledge()
            }
        }
    }
}
