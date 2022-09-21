package no.nav.eessi.pensjon.pdl.adresseoppdatering

import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.models.EndringsmeldingKontaktAdresse
import no.nav.eessi.pensjon.models.EndringsmeldingUtenlandskAdresse
import no.nav.eessi.pensjon.models.PdlEndringOpplysning
import no.nav.eessi.pensjon.models.Personopplysninger
import no.nav.eessi.pensjon.models.SedHendelse
import no.nav.eessi.pensjon.pdl.adresseoppdatering.SedTilPDLAdresse.OK
import no.nav.eessi.pensjon.pdl.adresseoppdatering.SedTilPDLAdresse.Valideringsfeil
import no.nav.eessi.pensjon.pdl.validering.erRelevantForEESSIPensjon
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.PersonoppslagException
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endringstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Kontaktadresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.Opplysningstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Person
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class Adresseoppdatering(
    private val personService: PersonService,
    private val euxService: EuxService,
    private val sedTilPDLAdresse: SedTilPDLAdresse
) {
    private val logger = LoggerFactory.getLogger(Adresseoppdatering::class.java)
    private val secureLogger = LoggerFactory.getLogger("secureLog")

    fun oppdaterUtenlandskKontaktadresse(sedHendelse: SedHendelse): Result {
        logger.info("*** Starter pdl endringsmelding (ADRESSE) prosess for BucType: ${sedHendelse.bucType}, SED: ${sedHendelse.sedType}, RinaSakID: ${sedHendelse.rinaSakId} ***")

        if (!erRelevantForEESSIPensjon(sedHendelse)) {
            return NoUpdate("SED ikke relevant for EESSI Pensjon")
        }

        val sed = euxService.hentSed(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)

        secureLogger.debug("SED:\n$sed")

        val bruker = sed.nav?.bruker

        if (!isBrukersAdresseISEDIUtlandet(bruker)) {
            return NoUpdate("Bruker har ikke utenlandsk adresse i SED")
        }

        if (isSedHendelseAvsenderNull(sedHendelse)) {
            return Error("Mangler avsenderNavn eller avsenderLand i sedHendelse - avslutter adresseoppdatering: $sedHendelse")
        }

        if(!isSedHendelseFromPreprod(sedHendelse) && isAvsenderLandForskjelligFraAdressensLand(sedHendelse, sed)) {
            return NoUpdate("Adressens landkode (${sed.nav?.bruker?.adresse?.land}) er ulik landkode på avsenderland (${sedHendelse.avsenderLand}).")
        }

        if (!hasNorskPin(bruker)) {
            // TODO Håndtere brukere med ikke-norske identer
            return NoUpdate("Bruker har ikke norsk pin i SED")
        }

        val normalisertNorskPIN = try {
            normaliserNorskPin(norskPin(bruker)!!.identifikator!!)
        } catch (ex: IllegalArgumentException) {
            return NoUpdate("Brukers norske id fra SED validerer ikke: \"${norskPin(bruker)!!.identifikator!!}\" - ${ex.message}")
        }

        val personFraPDL = try {
            personService.hentPerson(NorskIdent(normalisertNorskPIN))
        } catch (ex: PersonoppslagException) {
            if (ex.code == "not_found") {
                return NoUpdate("Finner ikke bruker i PDL med angitt fnr i SED")
            }
            throw ex
        } ?: return Error("hentPerson returnerte null")

        secureLogger.debug("Person fra PDL:\n${personFraPDL.toJson()}")

        if (isAdressebeskyttet(personFraPDL.adressebeskyttelse)) {
            return NoUpdate("Ingen adresseoppdatering")
        }

        logger.info("Vi har funnet en person fra PDL med samme norsk identifikator som bruker i SED")

        if (!hasUtenlandskKontaktadresse(personFraPDL) || !sedTilPDLAdresse.isUtenlandskAdresseISEDMatchMedAdresseIPDL(bruker?.adresse!!, personFraPDL.kontaktadresse!!.utenlandskAdresse!!)) {
            return when (val konverteringResult = sedTilPDLAdresse.konverter(sedHendelse.avsenderNavn + " (" + sedHendelse.avsenderLand + ")", bruker?.adresse!!)) {
                is OK -> Update(
                    "Adressen i SED finnes ikke i PDL, sender OPPRETT endringsmelding",
                    pdlAdresseEndringOpplysning(
                        norskFnr = normalisertNorskPIN,
                        kilde = sedHendelse.avsenderNavn + " (" + sedHendelse.avsenderLand + ")",
                        endringsmeldingKontaktAdresse = konverteringResult.endringsmeldingKontaktAdresse
                    )
                )
                is Valideringsfeil -> NoUpdate("Adressen validerer ikke etter reglene til PDL: ${konverteringResult.description}")
            }
        }

        if (personFraPDL.kontaktadresse!!.gyldigFraOgMed?.toLocalDate() == LocalDate.now()) {
            return NoUpdate("Adresse finnes allerede i PDL med dagens dato som gyldig-fra-dato, dropper oppdatering")
        }

        return Update(
            "Adresse finnes allerede i PDL, oppdaterer gyldig til og fra dato",
            korrigerDatoEndringOpplysning(
                norskFnr = normalisertNorskPIN,
                kilde = sedHendelse.avsenderNavn + " (" + sedHendelse.avsenderLand + ")",
                kontaktadresse = personFraPDL.kontaktadresse!!
            )
        )

    }

    private fun normaliserNorskPin(norskPinFraSED: String) =
        Fodselsnummer.fraMedValidation(norskPinFraSED)!!.value
            .also {
                if (it != norskPinFraSED) {
                    logger.info("Fnr i SED på ustandard format - alt utenom tall fjernet.")
                }
            }

    private fun hasUtenlandskKontaktadresse(personFraPDL: Person) =
        personFraPDL.kontaktadresse?.utenlandskAdresse != null

    private fun hasNorskPin(bruker: Bruker?) = norskPin(bruker) != null

    private fun norskPin(bruker: Bruker?) =
        bruker?.person?.pin?.firstOrNull { it.land == "NO" }

    private fun isAvsenderLandForskjelligFraAdressensLand(sedHendelse: SedHendelse, sed: SED) =
        sedHendelse.avsenderLand != sed.nav?.bruker?.adresse?.land

    private fun isSedHendelseFromPreprod(sedHendelse: SedHendelse) =
        sedHendelse.avsenderId in listOf("NO:NAVAT05", "NO:NAVAT07")

    private fun isSedHendelseAvsenderNull(sedHendelse: SedHendelse) =
        sedHendelse.avsenderNavn == null || sedHendelse.avsenderLand == null

    private fun isBrukersAdresseISEDIUtlandet(bruker: Bruker?) =
        bruker?.adresse?.land != null && bruker.adresse?.land != "NO"

    fun korrigerDatoEndringOpplysning(
        norskFnr: String,
        kilde: String,
        kontaktadresse: Kontaktadresse
    ) = PdlEndringOpplysning(
        listOf(
            Personopplysninger(
                endringstype = Endringstype.KORRIGER,
                ident = norskFnr,
                endringsmelding = EndringsmeldingKontaktAdresse(
                    kilde = kilde,
                    gyldigFraOgMed = LocalDate.now(),
                    gyldigTilOgMed = LocalDate.now().plusYears(1),
                    coAdressenavn = kontaktadresse.coAdressenavn,
                    adresse = EndringsmeldingUtenlandskAdresse(
                        adressenavnNummer = kontaktadresse.utenlandskAdresse!!.adressenavnNummer,
                        bygningEtasjeLeilighet = kontaktadresse.utenlandskAdresse!!.bygningEtasjeLeilighet,
                        bySted = kontaktadresse.utenlandskAdresse!!.bySted,
                        landkode = kontaktadresse.utenlandskAdresse!!.landkode,
                        postboksNummerNavn = kontaktadresse.utenlandskAdresse!!.postboksNummerNavn,
                        postkode = kontaktadresse.utenlandskAdresse!!.postkode,
                        regionDistriktOmraade = kontaktadresse.utenlandskAdresse!!.regionDistriktOmraade
                    )
                ),
                opplysningstype = Opplysningstype.KONTAKTADRESSE,
                opplysningsId = kontaktadresse.metadata.opplysningsId
            )
        )
    )

    fun pdlAdresseEndringOpplysning(
        norskFnr: String,
        kilde: String,
        endringsmeldingKontaktAdresse: EndringsmeldingKontaktAdresse,
    ) = PdlEndringOpplysning(
        listOf(
            Personopplysninger(
                endringstype = Endringstype.OPPRETT,
                ident = norskFnr,
                endringsmelding = endringsmeldingKontaktAdresse,
                opplysningstype = Opplysningstype.KONTAKTADRESSE,
            )
        )
    )

    sealed class Result

    data class Update(val description: String, val pdlEndringsOpplysninger: PdlEndringOpplysning): Result()
    data class NoUpdate(val description: String): Result()
    data class Error(val description: String): Result()

}