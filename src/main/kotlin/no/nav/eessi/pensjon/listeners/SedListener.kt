package no.nav.eessi.pensjon.listeners

import io.micrometer.core.instrument.Metrics
import no.nav.eessi.pensjon.eux.EuxDokumentHelper
import no.nav.eessi.pensjon.eux.UtenlandskId
import no.nav.eessi.pensjon.eux.UtenlandskPersonIdentifisering
import no.nav.eessi.pensjon.handler.OppgaveHandler
import no.nav.eessi.pensjon.klienter.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.Endringsmelding
import no.nav.eessi.pensjon.models.PdlEndringOpplysning
import no.nav.eessi.pensjon.models.Personopplysninger
import no.nav.eessi.pensjon.models.SedHendelseModel
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import no.nav.eessi.pensjon.pdl.filtrering.PdlFiltrering
import no.nav.eessi.pensjon.pdl.validering.PdlValidering
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
import java.util.concurrent.*
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
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
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

            if (GyldigeHendelser.mottatt(sedHendelse)) {
                val bucType = sedHendelse.bucType!!
                logger.info("*** Starter pdl endringsmelding prosess for BucType: $bucType, SED: ${sedHendelse.sedType}, RinaSakID: ${sedHendelse.rinaSakId} ***")

                val alleGyldigeSED = dokumentHelper.alleGyldigeSEDForBuc(sedHendelse.rinaSakId, dokumentHelper.hentBuc(sedHendelse.rinaSakId))

                val utenlandskeIderFraSEDer = utenlandskPersonIdentifisering.finnAlleUtenlandskeIDerIMottatteSed(alleGyldigeSED)

                val identifisertePersoner = personidentifiseringService.hentIdentifisertePersoner(alleGyldigeSED, bucType)

                if (identifisertePersoner.isEmpty()) {
                    acknowledgment.acknowledge()
                    logger.info("Ingen identifiserte FNR funnet, Acket melding")
                    countEnhet("Ingen identifiserte FNR funnet")
                    return
                }

                if (identifisertePersoner.size > 1) {
                    acknowledgment.acknowledge()
                    logger.info("Antall identifiserte FNR er fler enn en, Acket melding")
                    countEnhet("Antall identifiserte FNR er fler enn en")
                    return
                }

                if (identifisertePersoner.first().erDoed) {
                    acknowledgment.acknowledge()
                    logger.info("Identifisert person registrert med doedsfall, kan ikke opprette endringsmelding. Acket melding")
                    countEnhet("Identifisert person registrert med doedsfall")
                    return
                }

                if (utenlandskeIderFraSEDer.size > 1) {
                    acknowledgment.acknowledge()
                    logger.info("Antall utenlandske IDer er flere enn en")
                    countEnhet("Antall utenlandske IDer er flere enn en")
                    return
                }

                if (utenlandskeIderFraSEDer.isEmpty()) {
                    acknowledgment.acknowledge()
                    logger.info("Ingen utenlandske IDer funnet i BUC")
                    countEnhet("Ingen utenlandske IDer funnet i BUC")
                    return
                }

                if (sedHendelse.avsenderLand == null || pdlValidering.erUidLandAnnetEnnAvsenderLand(utenlandskeIderFraSEDer.first(), sedHendelse.avsenderLand)) {
                    acknowledgment.acknowledge()
                    logger.info("Avsenderland mangler eller avsenderland er ikke det samme som uidland, stopper identifisering av personer")
                    countEnhet("Avsenderland mangler eller avsenderland er ikke det samme som uidland")
                    return
                }

                if (pdlFiltrering.finnesUidFraSedIPDL(identifisertePersoner.first().uidFraPdl, utenlandskeIderFraSEDer.first())) {
                    logger.info("PDLuid er identisk med SEDuid. Acket sedMottatt: ${cr.offset()}")
                    countEnhet("PDLuid er identisk med SEDuid")
                    acknowledgment.acknowledge()
                    return
                }

                //validering av uid korrekt format
                if (!pdlValidering.erPersonValidertPaaLand(utenlandskeIderFraSEDer.first())) {
                    logger.info("Ingen validerte identifiserte personer funnet. Acket sedMottatt: ${cr.offset()}")
                    countEnhet("Ingen validerte identifiserte personer funnet")
                    acknowledgment.acknowledge()
                    return
                }

                if (pdlFiltrering.skalOppgaveOpprettes(identifisertePersoner.first().uidFraPdl, utenlandskeIderFraSEDer.first())) {
                    // TODO: Denne koden er ikke lett å forstå - hva betyr returverdien?
                    //ytterligere sjekk om f.eks SWE fnr i PDL faktisk er identisk med sedUID (hvis så ikke opprett oppgave bare avslutt)
                    if (pdlFiltrering.sjekkYterligerePaaPDLuidMotSedUid(identifisertePersoner.first().uidFraPdl, utenlandskeIderFraSEDer.first())) {
                        logger.info("Det finnes allerede en annen uid fra samme land, opprette oppgave")
                        val result = oppgaveHandler.opprettOppgaveForUid(sedHendelse, utenlandskeIderFraSEDer.first(), identifisertePersoner.first())
                        // TODO: Det er litt rart at det logges slik når det nettopp er opprettet en oppgave ...
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
                        utenlandskeIderFraSEDer.first(),
                        identifisertePersoner.first().fnr!!.value,
                        avsender
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

    fun countEnhet(melding: String) {
        try {
            Metrics.counter("PDLmeldingSteg",   "melding", melding).increment()
        } catch (e: Exception) {
            logger.warn("Metrics feilet på enhet: $melding")
        }
    }

    fun sedHendelseMapping(hendelse: String): SedHendelseModel {
        val sedHendelseTemp = SedHendelseModel.fromJson(hendelse)

        //støtte avsenderland SE i testmiljø Q2
        return if (profile != "prod" && profile != "integrationtest") {
            sedHendelseTemp.copy(avsenderLand = "SE", avsenderNavn = "SE:test")
        } else {
            sedHendelseTemp
        }
    }
}
