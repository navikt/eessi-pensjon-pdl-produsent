package no.nav.eessi.pensjon.pdl.adresseoppdatering

import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.SedHendelseModel
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.CountDownLatch
import javax.annotation.PostConstruct

@Service
class AdresseListener (
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {
    val latch: CountDownLatch = CountDownLatch(1)
    private val logger = LoggerFactory.getLogger(AdresseListener::class.java)

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
                    val hendelseJson = SedHendelseModel.fromJson(hendelse)
                    logger.debug("Ser om sedHendelse allerede ligger i pdl med riktig adresse, rinaId: ${hendelseJson.rinaSakId}, bucType:${hendelseJson.bucType}, sedType:${hendelseJson.sedType}")

                    //TODO: kall til service for innhenting av opplysninger fra PDL

                    latch.countDown()
                }catch (ex: Exception){
                    logger.error("Noe gikk galt under behandling av SED-hendelse for adresse:\n $hendelse \n", ex)
                }
                //TODO: ack p?? alt mens det er under utvikling
                acknowledgment.acknowledge()
            }
        }
    }
}