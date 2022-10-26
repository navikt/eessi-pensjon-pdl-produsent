package no.nav.eessi.pensjon.pdl.identoppdatering

import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.retry.RetryCallback
import org.springframework.retry.RetryContext
import org.springframework.retry.listener.RetryListenerSupport
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.client.HttpClientErrorException
import java.util.*
import java.util.concurrent.CountDownLatch
import javax.annotation.PostConstruct

@Service
class SedListenerIdent(
        private val behandleIdentHendelse: SedHendelseIdentBehandler,
        @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {

    private val logger = LoggerFactory.getLogger(SedListenerIdent::class.java)
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
                    behandleIdentHendelse.behandle(hendelse)
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
}
