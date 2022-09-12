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

    fun oppdaterUtenlandskIdent(sedHendelse: SedHendelse): Resultat {
        if (erRelevantForEESSIPensjon(sedHendelse)) {
            val bucType = sedHendelse.bucType!!
            logger.info("*** Starter pdl endringsmelding prosess for BucType: $bucType, SED: ${sedHendelse.sedType}, RinaSakID: ${sedHendelse.rinaSakId} ***")

            val alleGyldigeSED = dokumentHelper.alleGyldigeSEDForBuc(sedHendelse.rinaSakId)

            val identifisertePersoner = personidentifiseringService.hentIdentifisertePersoner(alleGyldigeSED, bucType)

            if (identifisertePersoner.isEmpty()) {
                countEnhet("Ingen identifiserte FNR funnet")
                return NoUpdate("Ingen identifiserte FNR funnet, Acket melding")
            }

            if (identifisertePersoner.size > 1) {
                countEnhet("Antall identifiserte FNR er fler enn en")
                return NoUpdate("Antall identifiserte FNR er fler enn en, Acket melding")
            }

            val utenlandskeIderFraSEDer =
                utenlandskPersonIdentifisering.finnAlleUtenlandskeIDerIMottatteSed(alleGyldigeSED)

            if (utenlandskeIderFraSEDer.isEmpty()) {
                countEnhet("Ingen utenlandske IDer funnet i BUC")
                return NoUpdate("Ingen utenlandske IDer funnet i BUC")
            }

            if (utenlandskeIderFraSEDer.size > 1) {
                countEnhet("Antall utenlandske IDer er flere enn en")
                return NoUpdate("Antall utenlandske IDer er flere enn en")
            }

            // Vi har utelukket at det er 0 eller flere enn 1
            val utenlandskIdFraSed = utenlandskeIderFraSEDer.first()

            if (sedHendelse.avsenderLand.isNullOrEmpty() || pdlValidering.erUidLandAnnetEnnAvsenderLand(
                    utenlandskIdFraSed,
                    sedHendelse.avsenderLand
                )
            ) {
                countEnhet("Avsenderland mangler eller avsenderland er ikke det samme som uidland")
                return NoUpdate("Avsenderland mangler eller avsenderland er ikke det samme som uidland, stopper identifisering av personer")
            }

            val identifisertPersonFraPDL = identifisertePersoner.first()

            if (identifisertPersonFraPDL.erDoed) {
                countEnhet("Identifisert person registrert med doedsfall")
                return NoUpdate("Identifisert person registrert med doedsfall, kan ikke opprette endringsmelding. Acket melding")
            }

            if (pdlFiltrering.finnesUidFraSedIPDL(identifisertPersonFraPDL.uidFraPdl, utenlandskIdFraSed)) {
                countEnhet("PDLuid er identisk med SEDuid")
                return NoUpdate("PDLuid er identisk med SEDuid. Acket sedMottatt")
            }

            //validering av uid korrekt format
            if (!pdlValidering.erPersonValidertPaaLand(utenlandskIdFraSed)) {
                countEnhet("Ingen validerte identifiserte personer funnet")
                return NoUpdate("Ingen validerte identifiserte personer funnet. Acket sedMottatt")
            }

            if (pdlFiltrering.skalOppgaveOpprettes(identifisertPersonFraPDL.uidFraPdl, utenlandskIdFraSed)) {
                // TODO: Denne koden er ikke lett å forstå - hva betyr returverdien?
                //ytterligere sjekk om f.eks SWE fnr i PDL faktisk er identisk med sedUID (hvis så ikke opprett oppgave bare avslutt)
                if (pdlFiltrering.sjekkYterligerePaaPDLuidMotSedUid(
                        identifisertPersonFraPDL.uidFraPdl,
                        utenlandskIdFraSed
                    )
                ) {
                    logger.info("Det finnes allerede en annen uid fra samme land, opprette oppgave")
                    val result =
                        oppgaveHandler.opprettOppgaveForUid(sedHendelse, utenlandskIdFraSed, identifisertPersonFraPDL)
                    // TODO: Det er litt rart at det logges slik når det nettopp er opprettet en oppgave ...
                    if (result) countEnhet("Det finnes allerede en annen uid fra samme land (Oppgave)")
                } else {
                    countEnhet("PDLuid er identisk med SEDuid")
                return NoUpdate("Oppretter ikke oppgave, Det som finnes i PDL er faktisk likt det som finnes i SED, avslutter")
                }
            }

            //Utfører faktisk innsending av endringsmelding til PDL (ny UID)
            sedHendelse.avsenderNavn?.let { avsender ->
                val endringsmelding = lagEndringsMelding(utenlandskIdFraSed, identifisertPersonFraPDL.fnr!!.value, avsender)
                countEnhet("Innsending av endringsmelding")
                return endringsmelding
            }
        }
         return NoUpdate("")
    }

    fun lagEndringsMelding(utenlandskPin: UtenlandskId, norskFnr: String, kilde: String): Resultat {
        return Update("Oppdaterer PDL med Ny Utenlandsk Ident",
            PdlEndringOpplysning(
                listOf(
                    Personopplysninger(
                        endringstype = Endringstype.OPPRETT,
                        ident = norskFnr,
                        endringsmelding = EndringsmeldingUID(
                            identifikasjonsnummer = utenlandskPin.id,
                            utstederland = kodeverkClient.finnLandkode(utenlandskPin.land) ?: throw RuntimeException("Feil ved landkode"),
                            kilde = kilde
                        ),
                        opplysningstype = Opplysningstype.UTENLANDSKIDENTIFIKASJONSNUMMER
                    ))
            ),
        )
    }

    fun countEnhet(melding: String) {
        try {
            Metrics.counter("PDLmeldingSteg",   "melding", melding).increment()
        } catch (e: Exception) {
            logger.warn("Metrics feilet på enhet: $melding")
        }
    }

sealed class Resultat

data class Update(val description: String, val identOpplysninger: PdlEndringOpplysning): Resultat()
data class NoUpdate(val description: String): Resultat()
data class Error(val description: String): Resultat()

}
