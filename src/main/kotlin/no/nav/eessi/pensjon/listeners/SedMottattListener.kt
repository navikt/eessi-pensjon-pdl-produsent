package no.nav.eessi.pensjon.listeners

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.eux.EuxDokumentHelper
import no.nav.eessi.pensjon.eux.UtenlandskId
import no.nav.eessi.pensjon.eux.UtenlandskPersonIdentifisering
import no.nav.eessi.pensjon.eux.model.sed.SED
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
    private val utenlandskPersonIdentifisering: UtenlandskPersonIdentifisering,
    @Value("\${SPRING_PROFILES_ACTIVE}") private val profile: String,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())
) {

    private val logger = LoggerFactory.getLogger(SedMottattListener::class.java)

    private val latch = CountDownLatch(1)
    private lateinit var consumeIncomingSed: MetricsHelper.Metric

    private val pdlFiltrering = PdlFiltrering()

    fun getLatch() = latch

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

                logger.debug(hendelse)

                //Forsøker med denne en gang til 258088L
                try {
                    val offset = cr.offset()
                    logger.info("*** Offset $offset  Partition ${cr.partition()} ***")
                    val sedHendelse = SedHendelseModel.fromJson(hendelse)
                    if (GyldigeHendelser.erGyldigInnkommetSed(sedHendelse)) {
                        val bucType = sedHendelse.bucType!!
                        logger.info("*** Starter pdl endringsmelding prosess for BucType: $bucType, SED: ${sedHendelse.sedType}, RinaSakID: ${sedHendelse.rinaSakId} ***")

                        val alleGyldigeSED = hentAlleGyldigeSedFraBUC(sedHendelse)

                        //identifisere Person hent Person fra PDL valider Person
                        val utenlandskeIder = utenlandskPersonIdentifisering.hentAlleUtenlandskeIder(alleGyldigeSED)
                        val identifisertePersoner = personidentifiseringService.hentIdentifisertPersoner(alleGyldigeSED, bucType, sedHendelse.sedType, sedHendelse.rinaDokumentId)

                        if (!eridenterGyldige(
                                pdlValidering,
                                identifisertePersoner,
                                acknowledgment,
                                sedHendelse
                            )
                        ) return@measure

                        logger.debug("Validerer uid fra sed som ikke finnes i PDL: ${identifisertePersoner.size}")
                        val filtrerUidSomIkkeFinnesIPdl = pdlFiltrering.filtrerUidSomIkkeFinnesIPdl(identifisertePersoner, kodeverkClient, sedHendelse.avsenderNavn!!)
                        if(filtrerUidSomIkkeFinnesIPdl.isEmpty()) {
                            logger.info("Ingen filtrerte personer funnet Acket sedMottatt: ${cr.offset()}")
                            acknowledgment.acknowledge()
                            return@measure
                        }

                        logger.debug("Validerer uid fra sed: ${filtrerUidSomIkkeFinnesIPdl.size}")
                        val validerteIdenter = pdlValidering.personerValidertPaaLand(filtrerUidSomIkkeFinnesIPdl)
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

    private fun eridenterGyldige(
        pdlValidering: PdlValidering,
        identifisertePersoner: List<IdentifisertPerson>,
        acknowledgment: Acknowledgment,
        sedHendelse: SedHendelseModel
    ): Boolean {
        if (!pdlValidering.finnesIdentifisertePersoner(identifisertePersoner)) {
            acknowledgment.acknowledge()
            logger.info("Ingen identifiserte FNR funnet, Acket melding")
            return false
        }

        if (identifisertePersoner.size > 1) {
            acknowledgment.acknowledge()
            logger.info("Antall identifiserte FNR er fler enn en, Acket melding")
            return false
        }

        if (identifisertePersoner.first().personIdenterFraSed.uid.size > 1) {
            acknowledgment.acknowledge()
            logger.info("Antall utenlandske IDer er flere enn en")
            return false
        }

        if (sedHendelse.avsenderLand == null || pdlValidering.erUidLandAnnetEnnAvsenderLand(
                identifisertePersoner,
                sedHendelse.avsenderLand
            )
        ) {
            acknowledgment.acknowledge()
            logger.error("Avsenderland mangler eller avsenderland er ikke det samme som uidland, stopper identifisering av personer")
            return false
        }
        return true
    }

    private fun hentAlleGyldigeSedFraBUC(sedHendelse: SedHendelseModel): List<SED> {
        val buc = dokumentHelper.hentBuc(sedHendelse.rinaSakId)
        val alleGyldigeDokumenter = dokumentHelper.hentAlleGyldigeDokumenter(buc)
        return dokumentHelper.hentAlleSedIBuc(sedHendelse.rinaSakId, alleGyldigeDokumenter)
    }

    fun lagEndringsMelding(utenlandskPin: UtenlandskId,
                           norskFnr: String,
                           kilde: String){
        val pdlEndringsOpplysninger = PdlEndringOpplysning(
            listOf(
                Personopplysninger(
                    ident = norskFnr,
                    endringsmelding = Endringsmelding(
                        identifikasjonsnummer = utenlandskPin.id,
                        utstederland = utenlandskPin.land,
                        kilde = kilde
                    )
                )
            )
        )
        personMottakKlient.opprettPersonopplysning(pdlEndringsOpplysninger)
    }
}
