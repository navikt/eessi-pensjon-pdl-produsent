package no.nav.eessi.pensjon.pdl.dodsmelding

import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.person.pdl.leesah.Personhendelse
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service

@Service
class MeldingFraPdlListener(
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {
    private val logger = LoggerFactory.getLogger(MeldingFraPdlListener::class.java)
    private val leesahKafkaListenerMetric = metricsHelper.init("consumeMsgFromPdlDodsmelding")

    @KafkaListener(
        autoStartup = "\${pdl.kafka.autoStartup}",
        batch = "true",
        properties = [
            "auth.exception.retry.interval=30s",
            "auto.offset.reset=earliest",
            "value.deserializer=io.confluent.kafka.serializers.KafkaAvroDeserializer",
            "key.deserializer=org.apache.kafka.common.serialization.StringDeserializer",
            "specific.avro.reader=true",
        ]
    )
    fun mottaLeesahMelding(consumerRecords: List<ConsumerRecord<String, Personhendelse>>, ack: Acknowledgment) {
        try {
            logger.info("Behandler ${consumerRecords.size} meldinger, firstOffset=${consumerRecords.first().offset()}, lastOffset=${consumerRecords.last().offset()}")
            consumerRecords.forEach { record ->
                leesahKafkaListenerMetric.measure {
                    logger.info("Mottatt key fra ${record.key()}")
                    logger.info("Mottatt melding fra ${record.value()}")
                    val opplysningstype = record.value().get("opplysningstype").toString()
                    when (opplysningstype) {
                        "DOEDSFALL_V1" -> {
                            logger.info("Undersøker type:: ${opplysningstype}")
                        }
                        "BOSTEDSADRESSE_V1", "KONTAKTADRESSE_V1", "OPPHOLDSADRESSE_V1" -> {
                            logger.info("Undersøker type:: ${opplysningstype}")
                        }
                        else -> {
                            logger.debug("Fant ikke type: ${opplysningstype}, Det er helt OK!")
                        }
                    }
                    Thread.sleep(5000) // Slow down processing by 5 seconds per record
                }
            }
        } catch (e: Exception) {
            logger.error("Behandling av hendelse feilet", e)
            throw e
        }
        ack.acknowledge()
        logger.info("Acket personhendelse")
    }
}