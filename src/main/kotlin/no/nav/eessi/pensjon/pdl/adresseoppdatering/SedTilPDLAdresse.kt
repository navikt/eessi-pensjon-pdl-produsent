package no.nav.eessi.pensjon.pdl.adresseoppdatering

import no.nav.eessi.pensjon.eux.model.sed.Adresse
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.kodeverk.LandkodeException
import no.nav.eessi.pensjon.pdl.EndringsmeldingKontaktAdresse
import no.nav.eessi.pensjon.pdl.EndringsmeldingUtenlandskAdresse
import no.nav.eessi.pensjon.pdl.adresseoppdatering.AdresseValidering.erGyldigAdressenavnNummerEllerBygningEtg
import no.nav.eessi.pensjon.pdl.adresseoppdatering.AdresseValidering.erGyldigByStedEllerRegion
import no.nav.eessi.pensjon.pdl.adresseoppdatering.AdresseValidering.erGyldigPostKode
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskAdresse
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.Period

@Component
class SedTilPDLAdresse(private val kodeverkClient: KodeverkClient) {
    private val postBoksInfo = listOf("postboks", "postb", "postbox", "p.b", "po.box")

    companion object {
        val gyldighetsperiodeKontaktadresse = Period.ofYears(5)
    }

    /**
     * @throws IllegalArgumentException ved valideringsfeil
     */
    fun konverter(kilde: String, sedAdresse: Adresse) =
        EndringsmeldingKontaktAdresse(
            kilde = formaterVekkHakeParentes(kilde),
            gyldigFraOgMed = LocalDate.now(),
            gyldigTilOgMed = LocalDate.now().plus(gyldighetsperiodeKontaktadresse),
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
            // Denne er ikke spesifisert av PDL, men vi får valideringsfeil uten dette
            requireMinstEttFeltMedVerdi(listOf(
                it.adresse!!.adressenavnNummer,
                it.adresse.postboksNummerNavn)
            ) { "Ikke gyldig adresse, trenger enten adressenavnNummer eller postboksNummerNavn" }
            // Denne er spesifisert av PDL, men slår ikke til etter at vi innførte krav om postkode
            requireMinstEttFeltMedVerdi(listOf(
                it.adresse.adressenavnNummer,
                it.adresse.bygningEtasjeLeilighet,
                it.adresse.bySted,
                it.adresse.postboksNummerNavn,
                it.adresse.postkode,
                it.adresse.regionDistriktOmraade)
            ) { "Ikke gyldig adresse, har kun landkode" }
        }

    private fun formaterVekkHakeParentes(kilde: String): String =
        kilde.replace("->", "")
            .replace("<-", "")
            .replace("<", "")
            .replace(">", "")

    private fun requireMinstEttFeltMedVerdi(verdier: List<String?>, lazyMessage: () -> Any) {
        require(verdier.any { it.isNullOrEmpty().not() }, lazyMessage)
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
        requireNotNull(postnummer) { "Ikke gyldig postkode: null" }
            .also { require(erGyldigPostKode(it)) { "Ikke gyldig postkode: $it" } }

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
                && gateIAdresseNavnNummerEllerPostboksNummerNavn(sedAdresse.gate, pdlAdresse.adressenavnNummer, pdlAdresse.postboksNummerNavn)
                && blankIfNull(sedAdresse.bygning) == blankIfNull(pdlAdresse.bygningEtasjeLeilighet)
                && blankIfNull(sedAdresse.by) == blankIfNull(pdlAdresse.bySted)
                && blankIfNull(sedAdresse.postnummer) == blankIfNull(pdlAdresse.postkode)
                && blankIfNull(sedAdresse.region) == blankIfNull(pdlAdresse.regionDistriktOmraade)
                && sedAdresse.land == pdlAdresse.landkode.let { kodeverkClient.finnLandkode(it) }

    private fun gateIAdresseNavnNummerEllerPostboksNummerNavn(gate: String?, adresseNavnNummer: String?, postboksNummerNavn: String?) =
        ((listOf(adresseNavnNummer, postboksNummerNavn).filter { it.isNullOrEmpty().not() }.isEmpty() && adresseNavnNummer.isNullOrEmpty())
                || gate in listOf(adresseNavnNummer, postboksNummerNavn))

    private fun inneholderPostBoksInfo(gate: String) = postBoksInfo.any { gate.contains(it) }

    fun blankIfNull(text: String?) = text?:""
}