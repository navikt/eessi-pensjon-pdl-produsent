package no.nav.eessi.pensjon.pdl.identoppdateringgjenlev

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
                try {
                    if (cr.offset() in listOf(1072448L,1786617L, 1860315L)) {
                        logger.warn("Hopper over offset: ${cr.offset()} grunnet feil ved henting av vedlegg...")
                    } else {
                        runCatching {
                            behandleIdentHendelse.behandlenGjenlevHendelse(hendelse)
                        }.onSuccess {
                            logger.info("Acket sedGjenlevMottatt melding med offset: ${cr.offset()} i partisjon ${cr.partition()}")
                        }.onFailure {
                            logger.error("Noe gikk galt under behandling av SED-hendelse", it)
                            secureLogger.info("Noe gikk galt under behandling av SED-hendelse:\n$hendelse")
                            throw it
                        }
                    }
                    latch.countDown()
                    acknowledgment.acknowledge()
                } catch (ex: HttpClientErrorException) {
                    if (ex.statusCode == HttpStatus.LOCKED)
                        logger.error("Det pågår allerede en adresseoppdatering på bruker", ex)
                    else throw ex
                } catch (ex: Exception) {
                    logger.error("Noe gikk galt under behandling av SED-hendelse", ex)
                    throw ex
                }
            }
        }
    }
}

