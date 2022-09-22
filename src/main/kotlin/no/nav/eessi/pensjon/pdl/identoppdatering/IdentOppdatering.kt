package no.nav.eessi.pensjon.pdl.identoppdatering

import io.micrometer.core.instrument.Metrics
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.UtenlandskId
import no.nav.eessi.pensjon.eux.UtenlandskPersonIdentifisering
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.models.EndringsmeldingUID
import no.nav.eessi.pensjon.models.PdlEndringOpplysning
import no.nav.eessi.pensjon.models.Personopplysninger
import no.nav.eessi.pensjon.models.SedHendelse
import no.nav.eessi.pensjon.oppgave.OppgaveHandler
import no.nav.eessi.pensjon.pdl.filtrering.PdlFiltrering
import no.nav.eessi.pensjon.pdl.validering.PdlValidering
import no.nav.eessi.pensjon.pdl.validering.erRelevantForEESSIPensjon
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endringstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Opplysningstype
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service


@Service
class IdentOppdatering (
    private val dokumentHelper: EuxService,
    private val pdlFiltrering: PdlFiltrering,
    private val pdlValidering: PdlValidering,
    private val oppgaveHandler: OppgaveHandler,
    private val kodeverkClient: KodeverkClient,
    private val personidentifiseringService: PersonidentifiseringService,
    private val utenlandskPersonIdentifisering: UtenlandskPersonIdentifisering
) {

    private val logger = LoggerFactory.getLogger(IdentOppdatering::class.java)
    private val secureLogger = LoggerFactory.getLogger("secureLog")

    fun oppdaterUtenlandskIdent(sedHendelse: SedHendelse): Resultat {
        if (erRelevantForEESSIPensjon(sedHendelse)) {
            val bucType = sedHendelse.bucType!!
            logger.info("*** Starter pdl endringsmelding (IDENT) prosess for BucType: $bucType, SED: ${sedHendelse.sedType}, RinaSakID: ${sedHendelse.rinaSakId} ***")

            val alleGyldigeSED = dokumentHelper.alleGyldigeSEDForBuc(sedHendelse.rinaSakId)
            logger.info("Alle gyldige seder: \n$alleGyldigeSED")
            val identifisertePersoner = personidentifiseringService.hentIdentifisertePersoner(alleGyldigeSED, bucType)

            if (identifisertePersoner.isEmpty()) {
                count("Ingen identifiserte FNR funnet")
                return NoUpdate("Ingen identifiserte FNR funnet")
            }

            if (identifisertePersoner.size > 1) {
                count("Antall identifiserte FNR er fler enn en")
                return NoUpdate("Antall identifiserte FNR er fler enn en")
            }

            val utenlandskeIderFraSEDer =
                utenlandskPersonIdentifisering.finnAlleUtenlandskeIDerIMottatteSed(alleGyldigeSED)
            secureLogger.debug("Utenlandske IDer fra mottatt sed: $utenlandskeIderFraSEDer")

            if (utenlandskeIderFraSEDer.isEmpty()) {
                count("Ingen utenlandske IDer funnet i BUC")
                return NoUpdate("Ingen utenlandske IDer funnet i BUC")
            }

            if (utenlandskeIderFraSEDer.size > 1) {
                count("Antall utenlandske IDer er flere enn en")
                return NoUpdate("Antall utenlandske IDer er flere enn en")
            }

            // Vi har utelukket at det er 0 eller flere enn 1
            val utenlandskIdFraSed = utenlandskeIderFraSEDer.first()

            if (sedHendelse.avsenderLand.isNullOrEmpty() || pdlValidering.erUidLandAnnetEnnAvsenderLand(
                    utenlandskIdFraSed,
                    sedHendelse.avsenderLand
                )
            ) {
                count("Avsenderland mangler eller avsenderland er ikke det samme som uidland")
                return NoUpdate("Avsenderland mangler eller avsenderland er ikke det samme som uidland")
            }

            val identifisertPersonFraPDL = identifisertePersoner.first()

            if (identifisertPersonFraPDL.erDoed) {
                count("Identifisert person registrert med doedsfall")
                return NoUpdate("Identifisert person registrert med doedsfall")
            }

            //validering av uid korrekt format
            if (!pdlValidering.erPersonValidertPaaLand(utenlandskIdFraSed)) {
                count("Ingen validerte identifiserte personer funnet")
                return NoUpdate("Ingen validerte identifiserte personer funnet")
            }

            if (pdlFiltrering.finnesUidFraSedIPDL(identifisertPersonFraPDL.uidFraPdl, utenlandskIdFraSed)) {
                count("PDLuid er identisk med SEDuid")
                return NoUpdate("PDLuid er identisk med SEDuid")
            }

            if (pdlFiltrering.skalOppgaveOpprettes(identifisertPersonFraPDL.uidFraPdl, utenlandskIdFraSed)) {
                // TODO: Denne koden er ikke lett å forstå - hva betyr returverdien?
                //ytterligere sjekk om f.eks SWE fnr i PDL faktisk er identisk med sedUID (hvis så ikke opprett oppgave bare avslutt)
                if (!pdlFiltrering.uidIsedOgIPDLErFaktiskUlik(identifisertPersonFraPDL.uidFraPdl, utenlandskIdFraSed)) {
                    // Oppretter ikke oppgave, Det som finnes i PDL er faktisk likt det som finnes i SED, avslutter
                    count("PDLuid er identisk med SEDuid")
                    return NoUpdate("PDLuid er identisk med SEDuid")
                }
                if (oppgaveHandler.opprettOppgaveForUid(sedHendelse, utenlandskIdFraSed, identifisertPersonFraPDL)) {
                    count("Det finnes allerede en annen uid fra samme land (Oppgave)")
                    return NoUpdate("Det finnes allerede en annen uid fra samme land (Oppgave)")
                } else {
                    count("Oppgave opprettet tidligere")
                    return NoUpdate("Oppgave opprettet tidligere")
                }
            }

            //Utfører faktisk innsending av endringsmelding til PDL (ny UID)
            sedHendelse.avsenderNavn?.let { avsender ->
                logger.info("Oppdaterer PDL med Ny Utenlandsk Ident fra $avsender")
                val endringsmelding = Update(
                    "Innsending av endringsmelding",
                    pdlEndringOpplysning(identifisertPersonFraPDL.fnr!!.value, utenlandskIdFraSed, avsender),
                )
                count("Innsending av endringsmelding")
                return endringsmelding
            }
            count("AvsenderNavn er ikke satt, kan derfor ikke lage endringsmelding")
            return NoUpdate("AvsenderNavn er ikke satt, kan derfor ikke lage endringsmelding")
        }
        count("Ikke relevant for eessipensjon")
        return NoUpdate("Ikke relevant for eessipensjon")
    }

    private fun pdlEndringOpplysning(norskFnr: String, utenlandskPin: UtenlandskId, kilde: String) =
        PdlEndringOpplysning(
            listOf(
                Personopplysninger(
                    endringstype = Endringstype.OPPRETT,
                    ident = norskFnr,
                    endringsmelding = EndringsmeldingUID(
                        identifikasjonsnummer = konvertertTilPdlFormat(utenlandskPin),
                        utstederland = kodeverkClient.finnLandkode(utenlandskPin.land)
                            ?: throw RuntimeException("Feil ved landkode"),
                        kilde = kilde
                    ),
                    opplysningstype = Opplysningstype.UTENLANDSKIDENTIFIKASJONSNUMMER
                )
            )
        )

    private fun konvertertTilPdlFormat(utenlandskPin: UtenlandskId): String {
        val uid = utenlandskPin.id
        if (utenlandskPin.land == "SE") {
            if (uid.length == 10) uid.replaceRange(5, 5, "-")
            if (uid.length == 12) uid.replaceRange(7,7,"-")
        }
        return uid
    }

    fun count(melding: String) {
        try {
            Metrics.counter("PDLmeldingSteg",   "melding", melding).increment()
        } catch (e: Exception) {
            logger.warn("Metrics feilet på enhet: $melding")
        }
    }

sealed class Resultat

data class Update(val description: String, val identOpplysninger: PdlEndringOpplysning): Resultat()
data class NoUpdate(val description: String): Resultat()

}
