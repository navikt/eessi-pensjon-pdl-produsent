package no.nav.eessi.pensjon.pdl.dodsmelding

import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.person.pdl.leesah.Personhendelse
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import java.util.concurrent.CountDownLatch

@Service
class MeldingFraPdlListener(
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {
    val latch: CountDownLatch = CountDownLatch(1)
    private val logger = LoggerFactory.getLogger(MeldingFraPdlListener::class.java)
    private val secureLogger = LoggerFactory.getLogger("secureLog")
    private val leesahKafkaListenerMetric = metricsHelper.init("consumeMsgFromPdlDodsmelding")

    @KafkaListener(
        autoStartup = "\${pdl.kafka.autoStartup}",
        batch = "true",
        topics = ["pdl.leesah-v1"],
        properties = [
            "auth.exception.retry.interval: 30s",
            "auto.offset.reset:earliest",
            "value.deserializer:io.confluent.kafka.serializers.KafkaAvroDeserializer",
            "key.deserializer:io.confluent.kafka.serializers.KafkaAvroDeserializer",
            "specific.avro.reader:true",
        ],
    )
    fun mottaLeesahMelding(consumerRecords: List<ConsumerRecord<String, Personhendelse>>, ack: Acknowledgment) {
        try {
            logger.info("Behandler ${consumerRecords.size} meldinger, firstOffset=${consumerRecords.first().offset()}, lastOffset=${consumerRecords.last().offset()}")

            consumerRecords.forEach { record ->
                leesahKafkaListenerMetric.measure {
                    val opplysningstype = record.value().opplysningstype
                    if (opplysningstype == "DOEDSFALL_V1") {
                        MDC.put("personhendelseId", record.value().hendelseId)
                        logger.info("Undersøker type:: $opplysningstype")
                    } else {
                        logger.debug("Ingen behandling for: $opplysningstype")
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Behandling av hendelse feilet", e)
        }
        ack.acknowledge()
        logger.info("Acket personhendelse")
    }
}