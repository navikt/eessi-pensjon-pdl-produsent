package no.nav.eessi.pensjon.listeners

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.eux.EuxDokumentHelper
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.Endringsmelding
import no.nav.eessi.pensjon.models.PdlEndringOpplysning
import no.nav.eessi.pensjon.models.Personopplysninger
import no.nav.eessi.pensjon.models.SedHendelseModel
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import no.nav.eessi.pensjon.pdl.filtrering.PdlFiltrering
import no.nav.eessi.pensjon.pdl.validering.PdlValidering
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient
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
    private val personMottakKlient: PersonMottakKlient,
    private val kodeverkClient: KodeverkClient,
    @Value("\${SPRING_PROFILES_ACTIVE}") private val profile: String,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())
) {

    private val logger = LoggerFactory.getLogger(SedMottattListener::class.java)

    private val latch = CountDownLatch(1)
    private lateinit var consumeIncomingSed: MetricsHelper.Metric

    private val pdlFiltrering = PdlFiltrering()

    fun getLatch() = latch
    var result : Any? = null
    var resultat : Any? = null

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
                val pdlValidering =  PdlValidering()

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

                        if(!pdlValidering.finnesAvsenderInstitusjon(sedHendelse.avsenderNavn)){
                            acknowledgment.acknowledge()
                            logger.error("Institusjon mangler, stopper identifisering av personer")
                            return@measure
                        }

                        val bucType = sedHendelse.bucType!!

                        logger.info("*** Starter pdl endringsmelding prosess for BucType: $bucType, SED: ${sedHendelse.sedType}, RinaSakID: ${sedHendelse.rinaSakId} ***")

                        val currentSed = dokumentHelper.hentSed(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)

                        //identifisere Person hent Person fra PDL valider Person
                        val identifisertPersoner = personidentifiseringService.hentIdentifisertPersoner(
                            currentSed, bucType, sedHendelse.sedType, sedHendelse.rinaDokumentId
                        )

                        //kun for test
                        result = identifisertPersoner

                        if (!pdlValidering.finnesIdentifisertePersoner(identifisertPersoner)) {
                            no.nav.eessi.pensjon.personidentifisering.relasjoner.logger.info("Ingen identifiserte personer funnet Acket sedMottatt: ${cr.offset()}")
                            acknowledgment.acknowledge()
                            return@measure
                        }

                        logger.debug("Validerer uid fra sed som ikke finnes i PDL: ${identifisertPersoner.size}")
                        val filtrerUidSomIkkeFinnesIPdl = pdlFiltrering.filtrerUidSomIkkeFinnesIPdl(identifisertPersoner, kodeverkClient, sedHendelse.avsenderNavn!!)
                        if(filtrerUidSomIkkeFinnesIPdl.isEmpty()) {
                            logger.info("Ingen filtrerte personer funnet Acket sedMottatt: ${cr.offset()}")
                            acknowledgment.acknowledge()
                            return@measure

                        }

                        logger.debug("Validerer uid fra sed: ${filtrerUidSomIkkeFinnesIPdl.size}")
                        val validerteIdenter = pdlValidering.validerUid(filtrerUidSomIkkeFinnesIPdl, sedHendelse.avsenderLand!!)
                        if(validerteIdenter.isEmpty()) {
                            logger.info("Ingen validerte identifiserte personer funnet Acket sedMottatt: ${cr.offset()}")
                            acknowledgment.acknowledge()
                            return@measure
                        }
                        lagEndringsMelding(validerteIdenter)

                        //logikk for muligens oppgave

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


    fun lagEndringsMelding(identifisertPersoner: List<IdentifisertPerson>){
        identifisertPersoner.map { ident ->
            val uid = ident.personIdenterFraSed.uid.first()
            val fnr = ident.personIdenterFraSed.fnr?.value!!
            val pdlEndringsOpplysninger = PdlEndringOpplysning(
                listOf(
                    Personopplysninger(
                        ident = fnr,
                        endringsmelding = Endringsmelding(
                            identifikasjonsnummer = uid.identifikasjonsnummer,
                            utstederland = uid.utstederland,
                            kilde = uid.kilde
                        )
                    )
                )
            )
            personMottakKlient.opprettPersonopplysning(pdlEndringsOpplysninger)
        }
    }

}
