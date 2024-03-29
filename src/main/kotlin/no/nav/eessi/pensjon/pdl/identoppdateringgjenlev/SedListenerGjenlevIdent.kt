package no.nav.eessi.pensjon.pdl.identoppdateringgjenlev

import no.nav.eessi.pensjon.metrics.MetricsHelper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.CountDownLatch

@Service
class SedListenerGjenlevIdent(
        private val behandleIdentHendelse: SedHendelseGjenlevIdentBehandler,
        @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {

    private val logger = LoggerFactory.getLogger(SedListenerGjenlevIdent::class.java)
    private val latch = CountDownLatch(1)
    private lateinit var consumeIncomingSed: MetricsHelper.Metric
    private val secureLogger = LoggerFactory.getLogger("secureLog")

    fun getLatch() = latch

    init {
        consumeIncomingSed = metricsHelper.init("consumeIncomingSed")
    }

    @KafkaListener(
        containerFactory = "sedKafkaListenerContainerFactory",
        topics = ["\${kafka.sedMottatt.topic}"],
        groupId = "\${kafka.sedGjenlevMottatt.groupid}"
    )
    fun consumeSedMottatt(hendelse: String, cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        MDC.putCloseable("x_request_id", UUID.randomUUID().toString()).use {
            consumeIncomingSed.measure {
                logger.info("SedGjenlevMottatt i partisjon: ${cr.partition()}, med offset: ${cr.offset()}")
                runCatching {
                    behandleIdentHendelse.behandlenGjenlevHendelse(hendelse)
                }.onSuccess {
                    logger.info("Acket sedGjenlevMottatt melding med offset: ${cr.offset()} i partisjon ${cr.partition()}")
                    acknowledgment.acknowledge()
                    latch.countDown()

                }.onFailure {
                    logger.error("Noe gikk galt under behandling av SED-hendelse", it)
                    secureLogger.info("Noe gikk galt under behandling av SED-hendelse:\n$hendelse")
                    throw it
                }
            }

        }
    }
}
