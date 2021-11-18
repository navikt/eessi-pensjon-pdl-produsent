package no.nav.eessi.pensjon.listeners

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.eux.EuxDokumentHelper
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.SedHendelseModel
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.CountDownLatch
import javax.annotation.PostConstruct

@Service
class SedMottattListener(
    private val personidentifiseringService: PersonidentifiseringService,
    private val dokumentHelper: EuxDokumentHelper,
    @Value("\${SPRING_PROFILES_ACTIVE}") private val profile: String,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())
) {

    private val logger = LoggerFactory.getLogger(SedMottattListener::class.java)

    private val latch = CountDownLatch(1)
    private lateinit var consumeIncomingSed: MetricsHelper.Metric

    fun getLatch() = latch
    var result : Any? = null

    @PostConstruct
    fun initMetrics() {
        consumeIncomingSed = metricsHelper.init("consumeIncomingSed")
    }

    @KafkaListener(
        containerFactory = "onpremKafkaListenerContainerFactory",
        idIsGroup = false,
        topics = ["\${kafka.sedMottatt.topic}"],
        groupId = "\${kafka.sedMottatt.groupid}"
    )

    fun consumeSedMottatt(hendelse: String, cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        MDC.putCloseable("x_request_id", UUID.randomUUID().toString()).use {
            consumeIncomingSed.measure {

                logger.info("Innkommet sedMottatt hendelse i partisjon: ${cr.partition()}, med offset: ${cr.offset()}")
                if(cr.offset() == 0L && profile == "prod") {
                    logger.error("Applikasjonen har forsøkt å prosessere sedMottatt meldinger fra offset 0, stopper prosessering")
                    throw RuntimeException("Applikasjonen har forsøkt å prosessere sedMottatt meldinger fra offset 0, stopper prosessering")
                }
                logger.debug(hendelse)

                //Forsøker med denne en gang til 258088L
                try {
                    val offset = cr.offset()
                    logger.info("*** Offset $offset  Partition ${cr.partition()} ***")
                    val sedHendelse = SedHendelseModel.fromJson(hendelse)
                    if (GyldigeHendelser.mottatt(sedHendelse)) {

                        val bucType = sedHendelse.bucType!!

                        logger.info("*** Starter innkommende journalføring for BucType: $bucType, SED: ${sedHendelse.sedType}, RinaSakID: ${sedHendelse.rinaSakId} ***")

                        val currentSed = dokumentHelper.hentSed(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)

                        //identifisere Person hent Person fra PDL valider Person
                        val identifisertPersoner = personidentifiseringService.hentIdentifisertPersoner(
                            currentSed, bucType, sedHendelse.sedType, sedHendelse.rinaDokumentId
                        )

                        //kun for test
                        result = identifisertPersoner

                        if (identifisertPersoner.isEmpty()) {
                            logger.info("Ingen identifiserte personer funnet Acket sedMottatt: ${cr.offset()}")
                            acknowledgment.acknowledge()
                            return@measure
                        }

                        val personerUtenUtenlandskPinIPDL = identifisertPersoner.map { identifisertPerson ->  if(identifisertPerson.uid.filter { it.identifikasjonsnummer in identifisertPerson.personIdenter.uid} else null }

                        //logikk for veldigering av pdl-uid -> sed-uid

                        //logikk for validering av korrekt sed-uid

                        //logikk for muligens oppgave

                        //logikk for opprette pdl-endringsmelding

                    }

                    acknowledgment.acknowledge()
                    logger.info("Acket sedMottatt melding med offset: ${cr.offset()} i partisjon ${cr.partition()}")

                } catch (ex: Exception) {
                    logger.error("Noe gikk galt under behandling av mottatt SED-hendelse:\n $hendelse \n", ex)
                    acknowledgment.acknowledge();
                }
                latch.countDown()
            }
        }
    }
}
