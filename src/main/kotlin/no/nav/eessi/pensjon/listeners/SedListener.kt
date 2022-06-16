package no.nav.eessi.pensjon.listeners

import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.eux.EuxDokumentHelper
import no.nav.eessi.pensjon.eux.UtenlandskId
import no.nav.eessi.pensjon.eux.UtenlandskPersonIdentifisering
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.handler.OppgaveHandler
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.Endringsmelding
import no.nav.eessi.pensjon.models.PdlEndringOpplysning
import no.nav.eessi.pensjon.models.Personopplysninger
import no.nav.eessi.pensjon.models.SedHendelseModel
import no.nav.eessi.pensjon.models.erGyldig
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
class SedListener(
    private val personidentifiseringService: PersonidentifiseringService,
    private val dokumentHelper: EuxDokumentHelper,
    private val personMottakKlient: PersonMottakKlient,
    private val utenlandskPersonIdentifisering: UtenlandskPersonIdentifisering,
    private val pdlFiltrering: PdlFiltrering,
    private val pdlValidering: PdlValidering,
    private val kodeverkClient: KodeverkClient,
    private val oppgaveHandler: OppgaveHandler,
    @Value("\${SPRING_PROFILES_ACTIVE}") private val profile: String,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())
) {

    private val logger = LoggerFactory.getLogger(SedListener::class.java)

    private val latch = CountDownLatch(1)
    private lateinit var consumeIncomingSed: MetricsHelper.Metric

    fun getLatch() = latch

    @PostConstruct
    fun initMetrics() {
        consumeIncomingSed = metricsHelper.init("consumeIncomingSed")
    }

    @KafkaListener(
        containerFactory = "sedKafkaListenerContainerFactory",
        idIsGroup = false,
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
//        logger.info("$hendelsesType hendelse i partisjon: ${cr.partition()}, med offset: ${cr.offset()}")
        logger.debug(hendelse)
        logger.debug("Profile: $profile")

        try {
            val sedHendelse = sedHendelseMapping(hendelse)

            if (GyldigeHendelser.mottatt(sedHendelse)) {
                val bucType = sedHendelse.bucType!!
                logger.info("*** Starter pdl endringsmelding prosess for BucType: $bucType, SED: ${sedHendelse.sedType}, RinaSakID: ${sedHendelse.rinaSakId} ***")


                val buc = hentBuc(sedHendelse)

                val alleDocumenter = hentAlleDocumenter(buc)

                val alleGyldigeSED = hentAlleGyldigeSedFraBUC(sedHendelse, alleDocumenter)

                //trekk UT uident fra SED som kun er mottatt.
                val utenlandskeIderFraSed = utenlandskPersonIdentifisering.hentAlleUtenlandskeIder(alleGyldigeSED)
                //identifisere Person hent Person fra PDL valider Person
                val identifisertePersoner = personidentifiseringService.hentIdentifisertPersoner(
                    alleGyldigeSED,
                    bucType,
                    sedHendelse.sedType,
                    sedHendelse.rinaDokumentId
                )

                //litt logging
                loggingAvForenkledSed(alleDocumenter, alleGyldigeSED)

                if (!eridenterGyldige(
                        pdlValidering,
                        identifisertePersoner,
                        acknowledgment,
                        sedHendelse,
                        utenlandskeIderFraSed
                    )
                ) return

                logger.debug("Validerer uid fra sed som ikke finnes i PDL: ${identifisertePersoner.size}")
                val filtrerUidSomIkkeFinnesIPdl = pdlFiltrering.finnesUidFraSedIPDL(
                    identifisertePersoner.first().uidFraPdl,
                    utenlandskeIderFraSed.first()
                )
                //sjekk om PDLuid er identisk SEDuid (true så finnes de og er identiske)
                if (filtrerUidSomIkkeFinnesIPdl) {
                    logger.info("PDLuid er identisk med SEDuid Acket sedMottatt: ${cr.offset()}")
                    countEnhet("PDLuid er identisk med SEDuid")
                    acknowledgment.acknowledge()
                    return
                }

                //validering av uid korrekt format
                if (!pdlValidering.erPersonValidertPaaLand(utenlandskeIderFraSed.first())) {
                    logger.info("Ingen validerte identifiserte personer funnet Acket sedMottatt: ${cr.offset()}")
                    countEnhet("Ingen validerte identifiserte personer funnet")
                    acknowledgment.acknowledge()
                    return
                }

                //sjekk om det skal opprtte en oppgave
                if (pdlFiltrering.skalOppgaveOpprettes(
                        identifisertePersoner.first().uidFraPdl,
                        utenlandskeIderFraSed.first()
                    )
                ) {
                    //ytterligere sjekk om f.eks SWE fnr i PDL faktisk er identisk med sedUID (hvis så ikke opprett oppgave bare avslutt)
                    if (pdlFiltrering.sjekkYterligerePaaPDLuidMotSedUid(identifisertePersoner.first().uidFraPdl, utenlandskeIderFraSed.first())) {
                        logger.info("Det finnes allerede en annen uid fra samme land, opprette oppgave")
                        val result = oppgaveHandler.opprettOppgaveForUid(sedHendelse, utenlandskeIderFraSed.first(), identifisertePersoner.first())
                        if (result) countEnhet("Det finnes allerede en annen uid fra samme land (Oppgave)")
                    } else {
                        logger.info("Oppretter ikke oppgave, Det som finnes i PDL er faktisk likt det som finnes i SED, avslutter")
                        countEnhet("PDLuid er identisk med SEDuid")
                    }
                    acknowledgment.acknowledge()
                    return
                }

                //Utfører faktisk innsending av endringsmelding til PDL (ny UID)
                sedHendelse.avsenderNavn?.let { avsender ->
                    lagEndringsMelding(
                        utenlandskeIderFraSed.first(), identifisertePersoner.first().fnr!!.value, avsender
                    )
                    countEnhet("Innsending av endringsmelding")
                }

            }

            acknowledgment.acknowledge()
            logger.info("Acket sedMottatt melding med offset: ${cr.offset()} i partisjon ${cr.partition()}")

        } catch (ex: Exception) {
            logger.error("Noe gikk galt under behandling av SED-hendelse:\n $hendelse \n", ex)
            countEnhet("Noe gikk galt under behandling av SED-hendelse")
            acknowledgment.acknowledge()
        }
    }

    private fun eridenterGyldige(
        pdlValidering: PdlValidering,
        identifisertePersoner: List<IdentifisertPerson>,
        acknowledgment: Acknowledgment,
        sedHendelse: SedHendelseModel,
        utenlandskeIder: List<UtenlandskId>
    ): Boolean {

        if (!pdlValidering.finnesIdentifisertePersoner(identifisertePersoner)) {
            acknowledgment.acknowledge()
            logger.info("Ingen identifiserte FNR funnet, Acket melding")
            countEnhet("Ingen identifiserte FNR funnet")
            return false
        }

        if (identifisertePersoner.size > 1) {
            acknowledgment.acknowledge()
            logger.info("Antall identifiserte FNR er fler enn en, Acket melding")
            countEnhet("Antall identifiserte FNR er fler enn en")
            return false
        }

        if (identifisertePersoner.first().erDoed) {
            acknowledgment.acknowledge()
            logger.info("Identifisert person registrert med doedsfall, kan ikke opprette endringsmelding. Acket melding")
            countEnhet("Identifisert person registrert med doedsfall")
            return false
        }

        if (utenlandskeIder.size > 1) {
            acknowledgment.acknowledge()
            logger.info("Antall utenlandske IDer er flere enn en")
            countEnhet("Antall utenlandske IDer er flere enn en")
            return false
        }

        if (utenlandskeIder.isEmpty()) {
            acknowledgment.acknowledge()
            logger.info("Ingen utenlandske IDer funnet i BUC")
            countEnhet("Ingen utenlandske IDer funnet i BUC")
            return false
        }

        if (sedHendelse.avsenderLand == null || pdlValidering.erUidLandAnnetEnnAvsenderLand(utenlandskeIder.first(), sedHendelse.avsenderLand)) {
            acknowledgment.acknowledge()
            logger.info("Avsenderland mangler eller avsenderland er ikke det samme som uidland, stopper identifisering av personer")
            countEnhet("Avsenderland mangler eller avsenderland er ikke det samme som uidland")
            return false
        }
        return true
    }

    private fun hentBuc(sedHendelse: SedHendelseModel): Buc = dokumentHelper.hentBuc(sedHendelse.rinaSakId)

    private fun hentAlleDocumenter(buc: Buc): List<ForenkletSED> = dokumentHelper.hentAlleDocumenter(buc)

    private fun hentAlleGyldigeSedFraBUC(sedHendelse: SedHendelseModel, docs: List<ForenkletSED>): List<Pair<ForenkletSED, SED>> {
        val alleGyldigDokuenter = docs
            .filter { it.type.erGyldig() }
            .also { logger.info("Fant ${it.size} dokumenter i BUC: $it") }
        return dokumentHelper.hentAlleSedIBuc(sedHendelse.rinaSakId , alleGyldigDokuenter)
    }

    fun lagEndringsMelding(utenlandskPin: UtenlandskId,
                           norskFnr: String,
                           kilde: String)  {
        val pdlEndringsOpplysninger = PdlEndringOpplysning(
            listOf(
                Personopplysninger(
                    ident = norskFnr,
                    endringsmelding = Endringsmelding(
                        identifikasjonsnummer = utenlandskPin.id,
                        utstederland = kodeverkClient.finnLandkode(utenlandskPin.land) ?: throw RuntimeException("Feil ved landkode"),
                        kilde = kilde
                    )
                )
            )
        )
        personMottakKlient.opprettPersonopplysning(pdlEndringsOpplysninger)
    }

    fun loggingAvForenkledSed(alledocs : List<ForenkletSED> , list: List<Pair<ForenkletSED, SED>>) {
        logger.debug("Ufiltrert Sed i buc")
        logger.debug("Antall Sed i buc: ${alledocs.size }")
        logger.debug("Antall Sed mottatt i buc: ${alledocs.filter { doc -> doc.status == SedStatus.RECEIVED }.size }")
        logger.debug("Antall Sed sendt i buc: ${alledocs.filter { doc -> doc.status == SedStatus.SENT }.size }")
        logger.debug("*".repeat(20))
        logger.debug("Filtrerte gyldige Sed i buc: ${alledocs.size }")
        logger.debug("Antall Sed i buc: ${list.filter { (doc, _) -> doc.harGyldigStatus() }.size }")
        logger.debug("Antall Sed mottatt i buc: ${list.filter { (doc, _) -> doc.status == SedStatus.RECEIVED }.size }")
        logger.debug("Antall Sed sendt i buc: ${list.filter { (doc, _) -> doc.status == SedStatus.SENT }.size }")
        logger.debug("*".repeat(20))
    }

    fun countEnhet(melding: String) {
        try {
            Metrics.counter("PDLmeldingSteg",   "melding", melding).increment()
        } catch (e: Exception) {
            logger.warn("Metrics feilet på enhet: $melding")
        }
    }

    fun sedHendelseMapping(hendelse: String): SedHendelseModel {
        val sedHendelseTemp = SedHendelseModel.fromJson(hendelse)

        //støtte avsenderland SE i testmiljø Q1 og Q2
        return if (profile != "prod" && profile != "integrationtest") {
            sedHendelseTemp.copy(avsenderLand = "SE", avsenderNavn = "SE:test")
        } else {
            sedHendelseTemp
        }
    }
}
