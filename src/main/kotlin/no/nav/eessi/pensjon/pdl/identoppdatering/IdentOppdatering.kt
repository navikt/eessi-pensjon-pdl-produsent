package no.nav.eessi.pensjon.pdl.identoppdatering

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
        require(erRelevantForEESSIPensjon(sedHendelse)) { return NoUpdate("Ikke relevant for eessipensjon") }

        val alleGyldigeSED = dokumentHelper.alleGyldigeSEDForBuc(sedHendelse.rinaSakId).also { secureLogger.debug("Alle gyldige seder: \n$it") }

        val identifisertePersoner = personidentifiseringService.hentIdentifisertePersoner(alleGyldigeSED, sedHendelse.bucType!!)
        require(identifisertePersoner.isNotEmpty()) { return NoUpdate("Ingen identifiserte FNR funnet") }
        require(identifisertePersoner.size <= 1) { return NoUpdate("Antall identifiserte FNR er fler enn en") }

        val utenlandskeIderFraSEDer =
            utenlandskPersonIdentifisering.finnAlleUtenlandskeIDerIMottatteSed(alleGyldigeSED)
                .also { secureLogger.debug("Utenlandske IDer fra mottatt sed: $it") }
        require(utenlandskeIderFraSEDer.isNotEmpty()) { return NoUpdate("Ingen utenlandske IDer funnet i BUC") }
        require(utenlandskeIderFraSEDer.size <= 1) { return NoUpdate("Antall utenlandske IDer er flere enn en") }
        // Vi har utelukket at det er 0 eller flere enn 1
        val utenlandskIdFraSed = utenlandskeIderFraSEDer.first()

        require(pdlValidering.avsenderLandHarVerdiOgErSammeSomIdLand(utenlandskIdFraSed, sedHendelse.avsenderLand)) {
            return NoUpdate("Avsenderland mangler eller avsenderland er ikke det samme som uidland")
        }

        val identifisertPersonFraPDL = identifisertePersoner.first()

        require(!identifisertPersonFraPDL.erDoed) { return NoUpdate("Identifisert person registrert med doedsfall") }

        //validering av uid korrekt format
        require(pdlValidering.erPersonValidertPaaLand(utenlandskIdFraSed)) { return NoUpdate("Ingen validerte identifiserte personer funnet") }

        require(!pdlFiltrering.finnesUidFraSedIPDL(identifisertPersonFraPDL.uidFraPdl, utenlandskIdFraSed)) { return NoUpdate("PDLuid er identisk med SEDuid") }

        if (pdlFiltrering.skalOppgaveOpprettes(identifisertPersonFraPDL.uidFraPdl, utenlandskIdFraSed)) {
            //ytterligere sjekk om f.eks SWE fnr i PDL faktisk er identisk med sedUID (hvis så ikke opprett oppgave bare avslutt)
            require(pdlFiltrering.uidIsedOgIPDLErFaktiskUlik(identifisertPersonFraPDL.uidFraPdl, utenlandskIdFraSed)) {
                // Oppretter ikke oppgave, Det som finnes i PDL er faktisk likt det som finnes i SED, avslutter
                return NoUpdate("PDLuid er identisk med SEDuid")
            }
            return if (oppgaveHandler.opprettOppgaveForUid(sedHendelse, utenlandskIdFraSed, identifisertPersonFraPDL)) {
                NoUpdate("Det finnes allerede en annen uid fra samme land (Oppgave)")
            } else {
                NoUpdate("Oppgave opprettet tidligere")
            }
        }

        require(sedHendelse.avsenderNavn != null) { return NoUpdate("AvsenderNavn er ikke satt, kan derfor ikke lage endringsmelding") }

        logger.info("Oppdaterer PDL med Ny Utenlandsk Ident fra ${sedHendelse.avsenderNavn}")
        return Update(
            "Innsending av endringsmelding",
            pdlEndringOpplysning(identifisertPersonFraPDL.fnr!!.value, utenlandskIdFraSed, sedHendelse.avsenderNavn),
        ).also { secureLogger.debug(it.toString()) }
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
            if (uid.length == 12) uid.replaceRange(7, 7, "-")
        }
        return uid
    }

    sealed class Resultat {
        abstract val description: String
    }

    data class Update(override val description: String, val identOpplysninger: PdlEndringOpplysning) : Resultat()
    data class NoUpdate(override val description: String) : Resultat()
}