package no.nav.eessi.pensjon.pdl.adresseoppdatering

import no.nav.eessi.pensjon.eux.model.sed.Adresse
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.kodeverk.LandkodeException
import no.nav.eessi.pensjon.models.EndringsmeldingKontaktAdresse
import no.nav.eessi.pensjon.models.EndringsmeldingUtenlandskAdresse
import no.nav.eessi.pensjon.pdl.adresseoppdatering.AdresseValidering.erGyldigAdressenavnNummerEllerBygningEtg
import no.nav.eessi.pensjon.pdl.adresseoppdatering.AdresseValidering.erGyldigByStedEllerRegion
import no.nav.eessi.pensjon.pdl.adresseoppdatering.AdresseValidering.erGyldigPostKode
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskAdresse
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class SedTilPDLAdresse(private val kodeverkClient: KodeverkClient) {
    private val postBoksInfo = listOf("postboks", "postb", "postbox", "p.b", "po.box")

    /**
     * @throws IllegalArgumentException ved valideringsfeil
     */
    fun konverter(kilde: String, sedAdresse: Adresse) =
        EndringsmeldingKontaktAdresse(
            kilde = kilde,
            gyldigFraOgMed = LocalDate.now(),
            gyldigTilOgMed = LocalDate.now().plusYears(1),
            coAdressenavn = null,
            adresse = EndringsmeldingUtenlandskAdresse(
                adressenavnNummer = adresseNavnNummerFra(sedAdresse.gate),
                bygningEtasjeLeilighet = bygningEtasjeLeilighetFra(sedAdresse.bygning),
                bySted = byStedFra(sedAdresse.by),
                landkode = landKodeFra(sedAdresse.land)!!,
                postboksNummerNavn = postboksNummerNavnFra(sedAdresse.gate),
                postkode = postKodeFra(sedAdresse.postnummer),
                regionDistriktOmraade = regionDistriktOmraadeFra(sedAdresse.region)
            )
        ).also {
            requireMinstEttFeltMedVerdi(listOf(
                it.adresse!!.adressenavnNummer,
                it.adresse.bygningEtasjeLeilighet,
                it.adresse.bySted,
                it.adresse.postboksNummerNavn,
                it.adresse.postkode,
                it.adresse.regionDistriktOmraade)
            )
    }

    private fun requireMinstEttFeltMedVerdi(verdier: List<String?>) {
        require(verdier.any { it.isNullOrEmpty().not() }) { "Ikke gyldig adresse, har kun landkode" }
    }

    private fun postboksNummerNavnFra(gate: String?) =
        gate?.let { if (inneholderPostBoksInfo(it)) it else null }

    private fun bygningEtasjeLeilighetFra(bygning: String?) =
        bygning?.also { require(erGyldigAdressenavnNummerEllerBygningEtg(it)) { "Ikke gyldig bygningEtasjeLeilighet: $it" } }

    private fun landKodeFra(land: String?): String? {
        require(land != null) { "Mangler landkode" }
        return try {
            kodeverkClient.finnLandkode(land)
            } catch (ex: LandkodeException) {
                throw IllegalArgumentException("Ugyldig landkode: ${land}", ex)
            }
    }

    private fun postKodeFra(postnummer: String?) =
        postnummer?.also { require(erGyldigPostKode(it)) { "Ikke gyldig postkode: $it" } }

    private fun regionDistriktOmraadeFra(region: String?) =
        region?.also { require(erGyldigByStedEllerRegion(it)) { "Ikke gyldig regionDistriktOmraade: $it" } }

    private fun byStedFra(by: String?) =
        by?.also { require(erGyldigByStedEllerRegion(it)) { "Ikke gyldig bySted: $it" } }

    private fun adresseNavnNummerFra(gate: String?) =
        gate?.let { if (inneholderPostBoksInfo(it)) null else it }
            ?.also { require(erGyldigAdressenavnNummerEllerBygningEtg(it)) { "Ikke gyldig adressenavnNummer: $it" } }

    @SuppressWarnings("kotlin:S1067") // enkel struktur gir lav kognitiv load
    fun isUtenlandskAdresseISEDMatchMedAdresseIPDL(sedAdresse: Adresse, pdlAdresse: UtenlandskAdresse?) =
        pdlAdresse != null
                && sedAdresse.gate in listOf(pdlAdresse.adressenavnNummer, pdlAdresse.postboksNummerNavn)
                && sedAdresse.bygning == pdlAdresse.bygningEtasjeLeilighet
                && sedAdresse.by == pdlAdresse.bySted
                && sedAdresse.postnummer == pdlAdresse.postkode
                && sedAdresse.region == pdlAdresse.regionDistriktOmraade
                && sedAdresse.land == pdlAdresse.landkode.let { kodeverkClient.finnLandkode(it) }

    private fun inneholderPostBoksInfo(gate: String) = postBoksInfo.any { gate.contains(it) }
}