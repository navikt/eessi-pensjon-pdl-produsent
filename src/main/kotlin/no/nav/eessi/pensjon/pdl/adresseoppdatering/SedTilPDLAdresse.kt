package no.nav.eessi.pensjon.pdl.adresseoppdatering

import no.nav.eessi.pensjon.eux.model.sed.Adresse
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.kodeverk.LandkodeException
import no.nav.eessi.pensjon.models.EndringsmeldingKontaktAdresse
import no.nav.eessi.pensjon.models.EndringsmeldingUtenlandskAdresse
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class SedTilPDLAdresse(private val kodeverkClient: KodeverkClient) {

    private val postBoksInfo = listOf("postboks", "postb", "postbox", "p.b", "po.box")

    fun konverter(kilde: String, sedAdresse: Adresse): Result {
        val land = sedAdresse.land
        if (land == null) {
            return Valideringsfeil("Mangler landkode")
        }

        val landkode = try {
            kodeverkClient.finnLandkode(land)
        } catch (ex: LandkodeException) {
            return  Valideringsfeil("Ugyldig landkode: $land")
        }

        val adressenavnNummer = if (sedAdresse.gate != null && !inneholderPostBoksInfo(sedAdresse.gate)) sedAdresse.gate else null
        if (adressenavnNummer != null && !AdresseValidering.erGyldigAdressenavnNummerEllerBygningEtg(adressenavnNummer)) {
            return Valideringsfeil("Ikke gyldig adressenavnNummer: $adressenavnNummer")
        }

        val bySted = sedAdresse.by
        if (bySted != null && !AdresseValidering.erGyldigByStedEllerRegion(bySted)) {
            return Valideringsfeil("Ikke gyldig bySted: $bySted")
        }

        val regionDistriktOmraade = sedAdresse.region
        if (regionDistriktOmraade != null && !AdresseValidering.erGyldigByStedEllerRegion(regionDistriktOmraade)) {
            return Valideringsfeil("Ikke gyldig regionDistriktOmraade: $regionDistriktOmraade")
        }

        val postkode = sedAdresse.postnummer
        if (postkode != null && !AdresseValidering.erGyldigPostKode(postkode)) {
            return Valideringsfeil("Ikke gyldig postkode: $postkode")
        }

        val bygningEtasjeLeilighet = sedAdresse.bygning
        if (bygningEtasjeLeilighet != null && !AdresseValidering.erGyldigAdressenavnNummerEllerBygningEtg(bygningEtasjeLeilighet)) {
            return Valideringsfeil("Ikke gyldig bygningEtasjeLeilighet: $bygningEtasjeLeilighet")
        }

        val postboksNummerNavn = if (sedAdresse.gate != null && inneholderPostBoksInfo(sedAdresse.gate)) sedAdresse.gate else null

        if (adressenavnNummer.isNullOrEmpty() && bygningEtasjeLeilighet.isNullOrEmpty() && bySted.isNullOrEmpty() && postboksNummerNavn.isNullOrEmpty()
            && postkode.isNullOrEmpty() && regionDistriktOmraade.isNullOrEmpty()) {
            return Valideringsfeil("Ikke gyldig adresse, har kun landkode")
        }

        return OK(
            EndringsmeldingKontaktAdresse(
                kilde = kilde,
                gyldigFraOgMed = LocalDate.now(),
                gyldigTilOgMed = LocalDate.now().plusYears(1),
                coAdressenavn = null,
                adresse = EndringsmeldingUtenlandskAdresse(
                    adressenavnNummer = adressenavnNummer,
                    bygningEtasjeLeilighet = bygningEtasjeLeilighet,
                    bySted = bySted,
                    landkode = landkode!!,
                    postboksNummerNavn = postboksNummerNavn,
                    postkode = postkode,
                    regionDistriktOmraade = regionDistriktOmraade
                )
            )
        )

    }

    private fun inneholderPostBoksInfo(gate: String?) = postBoksInfo.any { gate!!.contains(it) }

    sealed class Result
    data class OK(val endringsmeldingKontaktAdresse: EndringsmeldingKontaktAdresse): Result()
    data class Valideringsfeil(val description: String): Result()
}