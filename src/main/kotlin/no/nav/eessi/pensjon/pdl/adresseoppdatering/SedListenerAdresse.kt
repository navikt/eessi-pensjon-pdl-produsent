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
    private val adresseMetric = metricsHelper.init("consumeIncomingSedForAddress")
    private val offsetToSkip = setOf<Long>(2054085, 2055597, 2059170, 2063691, 2065990, 2125613, 2128492, 2128494, 2189146, 2204882, 2211528, 2211784, 2268141, 2282524, 2282544)

    @KafkaListener(
        containerFactory = "sedKafkaListenerContainerFactory",
        topics = ["\${kafka.utenlandskAdresse.topic}"],
        groupId = "\${kafka.utenlandskAdresse.groupid}"
    )
    fun consumeSedMottatt(hendelse: String, cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        MDC.putCloseable("x_request_id", UUID.randomUUID().toString()).use {
            adresseMetric.measure {
                logger.info("SED-hendelse mottatt i partisjon: ${cr.partition()}, med offset: ${cr.offset()} ")

                if (cr.offset() in offsetToSkip) {
                    logger.warn("Hopper over offset: ${cr.offset()}")
                    return@measure
                }

                try {
                    sedHendelseBehandler.behandle(hendelse)
                    latch.countDown()
                    logger.info("Acket sedMottatt melding med offset: ${cr.offset()} i partisjon ${cr.partition()}")
                } catch (ex: HttpClientErrorException) {
                    handleHttpClientErrorException(ex)
                } catch (ex: Exception) {
                    handleGeneralException(ex, hendelse)
                }
                acknowledgment.acknowledge()
            }
        }
    }

    private fun handleHttpClientErrorException(ex: HttpClientErrorException) {
        when {
            ex.statusCode == HttpStatus.LOCKED -> logger.error("Det pågår allerede en adresseoppdatering på bruker", ex)
            ex.message?.contains("Kontaktadressen er allerede registrert som oppholdsadresse") == true ->
                logger.warn("Kontaktadressen er allerede registrert som bostedsadresse, Ingen Oppdatering")
            else -> throw ex
        }
    }

    private fun handleGeneralException(ex: Exception, hendelse: String) {
        logger.error("Noe gikk galt under behandling av SED-hendelse for adresse", ex)
        secureLogger.info("Noe gikk galt under behandling av SED-hendelse for adresse:\n$hendelse")
        throw ex
    }
}