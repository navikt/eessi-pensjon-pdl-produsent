package no.nav.eessi.pensjon.pdl.adresseoppdatering

import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.sed.Adresse
import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.models.EndringsmeldingKontaktAdresse
import no.nav.eessi.pensjon.models.EndringsmeldingUtenlandskAdresse
import no.nav.eessi.pensjon.models.PdlEndringOpplysning
import no.nav.eessi.pensjon.models.Personopplysninger
import no.nav.eessi.pensjon.models.SedHendelse
import no.nav.eessi.pensjon.pdl.validering.erRelevantForEESSIPensjon
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.PersonoppslagException
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endringstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Kontaktadresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.Opplysningstype
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
        require(erRelevantForEESSIPensjon(sedHendelse)) { return NoUpdate("SED ikke relevant for EESSI Pensjon") }

        val sed = euxService.hentSed(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId).also { secureLogger.debug("SED:\n$it") }

        require(adresseErIUtlandet(adresseFra(sed))) { return NoUpdate("Bruker har ikke utenlandsk adresse i SED") }
        require(avsenderISedHendelse(sedHendelse)) { "Mangler avsenderNavn eller avsenderLand i sedHendelse - avslutter adresseoppdatering: $sedHendelse" }
        require(avsenderLandOgAdressensLandErSamme(sedHendelse, sed)) {
            return NoUpdate("Adressens landkode (${adresseFra(sed)?.land}) er ulik landkode på avsenderland (${sedHendelse.avsenderLand}).")
        }

        // TODO Håndtere brukere med ikke-norske identer
        require (hasNorskPin(brukerFra(sed))) { return NoUpdate("Bruker har ikke norsk pin i SED") }

        val normalisertNorskPIN = try {
            normaliserNorskPin(norskPin(brukerFra(sed))!!.identifikator!!)
        } catch (ex: IllegalArgumentException) {
            return NoUpdate("Brukers norske id fra SED validerer ikke: \"${norskPin(brukerFra(sed))!!.identifikator!!}\" - ${ex.message}")
        }

        val personFraPDL = try {
            personService.hentPerson(NorskIdent(normalisertNorskPIN)) ?: throw NullPointerException("hentPerson returnerte null")
        } catch (ex: PersonoppslagException) {
            if (ex.code == "not_found") {
                return NoUpdate("Finner ikke bruker i PDL med angitt fnr i SED")
            }
            throw ex
        }.also { secureLogger.debug("Person fra PDL:\n${it.toJson()}") }

        require(erUtenAdressebeskyttelse(personFraPDL.adressebeskyttelse)) { return NoUpdate("Ingen adresseoppdatering") }

        logger.info("Vi har funnet en person fra PDL med samme norsk identifikator som bruker i SED")

        if (sedTilPDLAdresse.isUtenlandskAdresseISEDMatchMedAdresseIPDL(adresseFra(sed)!!, personFraPDL.kontaktadresse?.utenlandskAdresse)) {
            require(LocalDate.now() != personFraPDL.kontaktadresse!!.gyldigFraOgMed?.toLocalDate()) {
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

        val endringsmelding = try {
            sedTilPDLAdresse.konverter(sedHendelse.avsenderNavn + " (" + sedHendelse.avsenderLand + ")", adresseFra(sed)!!)
        } catch (ex: IllegalArgumentException) {
            return NoUpdate("Adressen validerer ikke etter reglene til PDL: ${ex.message}")
        }

        return Update(
            "Adressen i SED finnes ikke i PDL, sender OPPRETT endringsmelding",
            opprettAdresseEndringOpplysning(normalisertNorskPIN, endringsmelding)
        )
    }

    private fun opprettAdresseEndringOpplysning(normalisertNorskPIN: String, endringsmelding: EndringsmeldingKontaktAdresse) =
        PdlEndringOpplysning(
            listOf(
                Personopplysninger(
                    endringstype = Endringstype.OPPRETT,
                    ident = normalisertNorskPIN,
                    endringsmelding = endringsmelding,
                    opplysningstype = Opplysningstype.KONTAKTADRESSE,
                )
            )
        )

    private fun normaliserNorskPin(norskPinFraSED: String) =
        Fodselsnummer.fraMedValidation(norskPinFraSED)!!.value
            .also {
                if (it != norskPinFraSED) {
                    logger.info("Fnr i SED på ustandard format - alt utenom tall fjernet.")
                }
            }

    private fun hasNorskPin(bruker: Bruker?) = norskPin(bruker) != null

    private fun norskPin(bruker: Bruker?) =
        bruker?.person?.pin?.firstOrNull { it.land == "NO" }

    private fun avsenderLandOgAdressensLandErSamme(sedHendelse: SedHendelse, sed: SED) =
        sedHendelse.avsenderLand == adresseFra(sed)?.land || isSedHendelseFromPreprod(sedHendelse) /* "alt" er fra Norge i preprod */

    private fun adresseFra(sed: SED) = brukerFra(sed)?.adresse

    private fun brukerFra(sed: SED) = sed.nav?.bruker

    private fun isSedHendelseFromPreprod(sedHendelse: SedHendelse) =
        sedHendelse.avsenderId in listOf("NO:NAVAT05", "NO:NAVAT07")

    private fun avsenderISedHendelse(sedHendelse: SedHendelse) =
        sedHendelse.avsenderNavn != null && sedHendelse.avsenderLand != null

    private fun adresseErIUtlandet(adresse: Adresse?) =
        adresse?.land != null && adresse.land != "NO"

    fun korrigerDatoEndringOpplysning(norskFnr: String, kilde: String, kontaktadresse: Kontaktadresse) =
        PdlEndringOpplysning(listOf(
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
        ))

    sealed class Result {
        abstract val description: String
    }

    data class Update(override val description: String, val pdlEndringsOpplysninger: PdlEndringOpplysning): Result()
    data class NoUpdate(override val description: String): Result()
}