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
import no.nav.eessi.pensjon.pdl.filtrering.PdlFiltrering
import no.nav.eessi.pensjon.pdl.validering.AdresseValidering
import no.nav.eessi.pensjon.pdl.validering.erRelevantForEESSIPensjon
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endringstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Kontaktadresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.Opplysningstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Person
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class Adresseoppdatering(
    private val personService: PersonService,
    private val euxService: EuxService,
    private val pdlFiltrering: PdlFiltrering,
    private val sedTilPDLAdresse: SedTilPDLAdresse
) {
    private val logger = LoggerFactory.getLogger(Adresseoppdatering::class.java)

    fun oppdaterUtenlandskKontaktadresse(sedHendelse: SedHendelse): Result {
        logger.info("SED mottatt, rinaId: ${sedHendelse.rinaSakId}, bucType:${sedHendelse.bucType}, sedType:${sedHendelse.sedType}")

        if (!erRelevantForEESSIPensjon(sedHendelse)) {
            return NoUpdate("SED ikke relevant for EESSI Pensjon")
        }

        val sed = euxService.hentSed(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)

        logger.debug("SED: $sed")

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

        if(!AdresseValidering().erGyldigByStedEllerRegion(bruker?.adresse?.by!!)){
            return NoUpdate("Adressens by inneholder ugyldig informasjon (${bruker.adresse?.by})")
        }

        if (!hasNorskPin(bruker)) {
            // TODO Håndtere brukere med ikke-norske identer
            return NoUpdate("Bruker har ikke norsk pin i SED")
        }

        val personFraPDL = personService.hentPerson(NorskIdent(norskPin(bruker)!!.identifikator!!))

        logger.debug("Person fra PDL: $personFraPDL")

        if (personFraPDL == null) {
            return NoUpdate("Bruker ikke funnet i PDL")
        }

        if (isAdressebeskyttet(personFraPDL.adressebeskyttelse)) {
            return NoUpdate("Ingen adresseoppdatering")
        }

        logger.info("Vi har funnet en person fra PDL med samme norsk identifikator som bruker i SED")

        if (!hasUtenlandskKontaktadresse(personFraPDL) || !pdlFiltrering.isUtenlandskAdresseISEDMatchMedAdresseIPDL(bruker.adresse!!, personFraPDL.kontaktadresse!!.utenlandskAdresse!!)) {
            return when (val konverteringResult = sedTilPDLAdresse.konverter(sedHendelse.avsenderNavn + " (" + sedHendelse.avsenderLand + ")", bruker.adresse!!)) {
                is OK -> Update(
                    "Adressen i SED finnes ikke i PDL, sender OPPRETT endringsmelding",
                    pdlAdresseEndringOpplysning(
                        norskFnr = norskPin(bruker)!!.identifikator!!,
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
                norskFnr = norskPin(bruker)!!.identifikator!!,
                kilde = sedHendelse.avsenderNavn + " (" + sedHendelse.avsenderLand + ")",
                kontaktadresse = personFraPDL.kontaktadresse!!
            )
        )

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