package no.nav.eessi.pensjon.pdl.adresseoppdatering

import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.sed.Adresse
import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.models.EndringsmeldingKontaktAdresse
import no.nav.eessi.pensjon.models.EndringsmeldingUtenlandskAdresse
import no.nav.eessi.pensjon.models.PdlEndringOpplysning
import no.nav.eessi.pensjon.models.Personopplysninger
import no.nav.eessi.pensjon.pdl.validering.erRelevantForEESSIPensjon
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.PersonoppslagException
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endringstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.Kontaktadresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.Opplysningstype
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class VurderAdresseoppdatering(
    private val personService: PersonService,
    private val euxService: EuxService,
    private val sedTilPDLAdresse: SedTilPDLAdresse
) {
    private val logger = LoggerFactory.getLogger(VurderAdresseoppdatering::class.java)
    private val secureLogger = LoggerFactory.getLogger("secureLog")

    fun vurderUtenlandskKontaktadresse(sedHendelse: SedHendelse): Result {
        require(erRelevantForEESSIPensjon(sedHendelse)) { return IngenOppdatering("SED ikke relevant for EESSI Pensjon, buc: ${sedHendelse.bucType}, sed: ${sedHendelse.sedType}, sektor: ${sedHendelse.sektorKode}", "SED ikke relevant for EESSI Pensjon") }

        val sed = euxService.hentSed(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId).also { secureLogger.debug("SED:\n$it") }

        require(adresseErIUtlandet(adresseFra(sed))) { return IngenOppdatering("Bruker har ikke utenlandsk adresse i SED") }
        require(avsenderISedHendelse(sedHendelse)) { "Mangler avsenderNavn eller avsenderLand i sedHendelse - avslutter adresseoppdatering: $sedHendelse" }
        require(avsenderLandOgAdressensLandErSamme(sedHendelse, sed)) {
            return IngenOppdatering(
                "Adressens landkode (${adresseFra(sed)?.land}) er ulik landkode på avsenderland (${sedHendelse.avsenderLand})",
                "Adressens landkode er ulik landkode på avsenderland")
        }

        // Når det ikke finnes norsk ID så er det helt fint at dette tas av Id & Fordeling
        require (hasNorskPin(brukerFra(sed))) { return IngenOppdatering("Bruker har ikke norsk pin i SED") }

        val personFraPDL = try {

            val normalisertNorskPIN = try {
                normaliserNorskPin(norskPin(brukerFra(sed))!!.identifikator!!)
            } catch (ex: IllegalArgumentException) {
                return IngenOppdatering(
                    "Brukers norske id fra SED validerer ikke: \"${norskPin(brukerFra(sed))!!.identifikator!!}\" - ${ex.message}",
                    "Brukers norske id fra SED validerer ikke",
                )
            }

            personService.hentPerson(NorskIdent(normalisertNorskPIN)) ?: throw NullPointerException("hentPerson returnerte null")
        } catch (ex: PersonoppslagException) {
            if (ex.code == "not_found") {
                return IngenOppdatering("Finner ikke bruker i PDL med angitt fnr i SED")
            }
            throw ex
        }.also { secureLogger.debug("Person fra PDL:\n${it.toJson()}") }

        val norskFolkeregisterIdent =
            personFraPDL.identer
                .firstOrNull { it.gruppe == IdentGruppe.FOLKEREGISTERIDENT }?.ident
                ?: personFraPDL.identer.first().ident

        require(erUtenAdressebeskyttelse(personFraPDL.adressebeskyttelse)) { return IngenOppdatering("Ingen adresseoppdatering") }

        logger.info("Vi har funnet en person fra PDL med samme norsk identifikator som bruker i SED")

        val utenlandskKontaktadresseRegistrertAvNAV =
            personFraPDL.kontaktadresse?.let { if (it.metadata.master.uppercase().equals("FREG")) null else it  }?.utenlandskAdresse

        if (sedTilPDLAdresse.isUtenlandskAdresseISEDMatchMedAdresseIPDL(adresseFra(sed)!!, utenlandskKontaktadresseRegistrertAvNAV)) {
            require(LocalDate.now() != personFraPDL.kontaktadresse!!.gyldigFraOgMed?.toLocalDate()) {
                return IngenOppdatering("Adresse finnes allerede i PDL med dagens dato som gyldig-fra-dato, dropper oppdatering")
            }
            return Oppdatering(
                "Adressen fra ${sedHendelse.avsenderLand} finnes allerede i PDL, oppdaterer gyldig til og fra dato",
                korrigerDatoEndringOpplysning(
                    norskFnr = norskFolkeregisterIdent,
                    kilde = sedHendelse.avsenderNavn + " (" + sedHendelse.avsenderLand + ")",
                    kontaktadresse = personFraPDL.kontaktadresse!!
                )
            , metricTagValueOverride = "Adressen finnes allerede i PDL, oppdaterer gyldig til og fra dato"
            )
        }

        val endringsmelding = try {
            sedTilPDLAdresse.konverter(sedHendelse.avsenderNavn + " (" + sedHendelse.avsenderLand + ")", adresseFra(sed)!!)
        } catch (ex: IllegalArgumentException) {
            return IngenOppdatering(
                "Adressen fra ${sedHendelse.avsenderLand} validerer ikke etter reglene til PDL: ${ex.message}",
                "Adressen validerer ikke etter reglene til PDL"
            )
        }

        return Oppdatering(
            "Adressen i SED fra ${sedHendelse.avsenderLand} finnes ikke i PDL, sender OPPRETT endringsmelding",
            opprettAdresseEndringOpplysning(norskFolkeregisterIdent, endringsmelding),
            metricTagValueOverride = "Adressen i SED finnes ikke i PDL, sender OPPRETT endringsmelding"
        )
    }

    private fun opprettAdresseEndringOpplysning(ident: String, endringsmelding: EndringsmeldingKontaktAdresse) =
        PdlEndringOpplysning(
            listOf(
                Personopplysninger(
                    endringstype = Endringstype.OPPRETT,
                    ident = ident,
                    endringsmelding = endringsmelding,
                    opplysningstype = Opplysningstype.KONTAKTADRESSE,
                )
            )
        )

    private fun normaliserNorskPin(norskPinFraSED: String) =
        Fodselsnummer.fraMedValidation(norskPinFraSED)!!.value
            .also {
                if (it != norskPinFraSED) {
                    logger.info("Fnr i SED på ustandard format - alt utenom tall fjernet")
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
                    gyldigTilOgMed = LocalDate.now().plus(SedTilPDLAdresse.gyldighetsperiodeKontaktadresse),
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
        abstract val metricTagValueOverride: String?

        val metricTagValue: String
            get() = metricTagValueOverride ?: description
    }

    data class Oppdatering(override val description: String, val pdlEndringsOpplysninger: PdlEndringOpplysning, override val metricTagValueOverride: String? = null): Result() {
        override fun toString() = "Oppdatering(description=$description, pdlEndringsOpplysninger=[omitted], metricTagValueOverride=$metricTagValueOverride)"
    }
    data class IngenOppdatering(override val description: String, override val metricTagValueOverride: String? = null): Result()
}