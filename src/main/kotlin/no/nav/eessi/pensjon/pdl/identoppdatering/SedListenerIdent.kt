package no.nav.eessi.pensjon.pdl.identoppdatering

import io.micrometer.core.instrument.Metrics
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.SedHendelse
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import no.nav.eessi.pensjon.pdl.identoppdatering.IdentOppdatering.NoUpdate
import no.nav.eessi.pensjon.pdl.identoppdatering.IdentOppdatering.Update
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.*
import javax.annotation.PostConstruct

@Service
class SedListenerIdent(
    private val personMottakKlient: PersonMottakKlient,
    private val identOppdatering: IdentOppdatering,
    @Value("\${SPRING_PROFILES_ACTIVE:}") private val profile: String,
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
                consumeHendelse(cr, hendelse, acknowledgment)
                latch.countDown()
            }
        }
    }

    private fun consumeHendelse(
        cr: ConsumerRecord<String, String>,
        hendelse: String,
        acknowledgment: Acknowledgment
    ) {
        logger.info("SedMottatt i partisjon: ${cr.partition()}, med offset: ${cr.offset()}")
        logger.debug(hendelse)
        logger.debug("Profile: $profile")

        try {
            val sedHendelse = sedHendelseMapping(hendelse)

            if (profile == "prod" && sedHendelse.avsenderId in listOf("NO:NAVAT05", "NO:NAVAT07")) {
                logger.error("Avsender id er ${sedHendelse.avsenderId}. Dette er testdata i produksjon!!!\n$sedHendelse")
                acknowledgment.acknowledge()
                return
            }

            val resultat = identOppdatering.oppdaterUtenlandskIdent(sedHendelse)

            when(resultat) {
                is NoUpdate -> logger.info(resultat.toString())
                is Update ->  {
                    personMottakKlient.opprettPersonopplysning(resultat.identOpplysninger)
                    logger.info(resultat.toString())
                }
            }

            acknowledgment.acknowledge()
            logger.info("Acket sedMottatt melding med offset: ${cr.offset()} i partisjon ${cr.partition()}")

        } catch (ex: Exception) {
            logger.error("Noe gikk galt under behandling av SED-hendelse:\n $hendelse \n", ex)
            throw ex
        }
    }

    fun count(melding: String) {
        try {
            Metrics.counter("PDLmeldingSteg",   "melding", melding).increment()
        } catch (e: Exception) {
            logger.warn("Metrics feilet på enhet: $melding")
        }
    }

    fun sedHendelseMapping(hendelse: String): SedHendelse {
        val sedHendelseTemp = SedHendelse.fromJson(hendelse)

        //støtte avsenderland SE i testmiljø Q2
        return if (profile != "prod" && profile != "integrationtest") {
            sedHendelseTemp.copy(avsenderLand = "SE", avsenderNavn = "SE:test")
        } else {
            sedHendelseTemp
        }
    }
}
