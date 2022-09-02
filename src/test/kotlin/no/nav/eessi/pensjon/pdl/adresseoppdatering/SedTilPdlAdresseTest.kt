package no.nav.eessi.pensjon.pdl.adresseoppdatering

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.model.sed.Adresse
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.kodeverk.LandkodeException
import no.nav.eessi.pensjon.models.EndringsmeldingKontaktAdresse
import no.nav.eessi.pensjon.models.EndringsmeldingUtenlandskAdresse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate

class SedTilPdlAdresseTest {

    var kodeverkClient: KodeverkClient = mockk(relaxed = true)

    @BeforeEach
    fun setup() {
        every { kodeverkClient.finnLandkode("DKK") } returns "DK"
        every { kodeverkClient.finnLandkode("DK") } returns "DKK"
        every { kodeverkClient.finnLandkode("NLD") } returns "NL"
        every { kodeverkClient.finnLandkode("NL") } returns "NLD"
    }

    @Test
    fun `konvertering av gateadresse`(){
        val sedAdresse = adresse(
            gate = "gate",
            bygning = "bygning",
            by = "by",
            postnummer = "postnummer",
            region = "region",
            land = "DK"
        )

        val pdlAdresse = SedTilPDLAdresse.OK(
            endringsmeldingKontaktAdresse(
                kilde = "kilde",
                gyldigFraOgMed = LocalDate.now(),
                gyldigTilOgMed = LocalDate.now().plusYears(1),
                coAdressenavn = null,
                adresse = endringsmeldingUtenlandskAdresse(
                    adressenavnNummer = "gate",
                    bygningEtasjeLeilighet = "bygning",
                    bySted = "by",
                    landkode = "DKK",
                    postkode = "postnummer",
                    regionDistriktOmraade = "region"
                )
            )
        )

        assertEquals(pdlAdresse, SedTilPDLAdresse(kodeverkClient).konverter("kilde", sedAdresse))
    }

    @Test
    fun `Gateadresse med postboksadresse skal fylles ut i postboksNummerNavn`() {
        val sedAdresse = adresse(gate = "postboks 123")
        val pdlAdresse = SedTilPDLAdresse.OK(
            endringsmeldingKontaktAdresse(
                adresse = endringsmeldingUtenlandskAdresse(
                    adressenavnNummer = null,
                    postboksNummerNavn = "postboks 123"
                )
            )
        )

        assertEquals(pdlAdresse, SedTilPDLAdresse(kodeverkClient).konverter("some kilde", sedAdresse))
    }

    @ParameterizedTest
    @CsvSource("ukjent", "vet ikke"
    )
    fun `Gitt en adresse der by inneholder ukjent, saa gjoer vi ingen oppdatering`(ugyldigOrd: String){
        assertEquals(
            SedTilPDLAdresse.Valideringsfeil("Ikke gyldig bySted: $ugyldigOrd"),
            SedTilPDLAdresse(kodeverkClient).konverter("some kilde", adresse(by = ugyldigOrd))
        )
    }

    @Test
    fun `Gateadresse med ukjent skal gi valideringfeil`() {
        assertEquals(
            SedTilPDLAdresse.Valideringsfeil("Ikke gyldig adressenavnNummer: ukjent badeland strasse"),
            SedTilPDLAdresse(kodeverkClient).konverter("some kilde", adresse(gate = "ukjent badeland strasse"))
        )
    }

    @Test
    fun `regionDistriktOmraade med bare tall skal gi valideringfeil`() {
        assertEquals(
            SedTilPDLAdresse.Valideringsfeil("Ikke gyldig regionDistriktOmraade: 165483546"),
            SedTilPDLAdresse(kodeverkClient).konverter("some kilde", adresse(region = "165483546"))
        )
    }

    @Test
    fun `postkode som inneholder kun tegn saa skal det gi valideringfeil`() {
        assertEquals(
            SedTilPDLAdresse.Valideringsfeil("Ikke gyldig postkode: ***"),
            SedTilPDLAdresse(kodeverkClient).konverter("some kilde", adresse(postnummer = "***"))
        )
    }

    @Test
    fun `bygningEtasjeLeilighet med ordet postboks skal gi valideringfeil`() {
        assertEquals(
            SedTilPDLAdresse.Valideringsfeil("Ikke gyldig bygningEtasjeLeilighet: postboks 321"),
            SedTilPDLAdresse(kodeverkClient).konverter("some kilde", adresse(bygning = "postboks 321"))
        )
    }

    @Test
    fun `Landkode som er null skal gi valideringfeil`() {
        assertEquals(
            SedTilPDLAdresse.Valideringsfeil("Mangler landkode"),
            SedTilPDLAdresse(kodeverkClient).konverter("some kilde", Adresse(land = null))
        )
    }

    @Test
    fun `Landkode som er ugyldig skal gi valideringfeil`() {
        every { kodeverkClient.finnLandkode("X") } .throws(LandkodeException(""))
        assertEquals(
            SedTilPDLAdresse.Valideringsfeil("Ugyldig landkode: X"),
            SedTilPDLAdresse(kodeverkClient).konverter("some kilde", Adresse(land = "X"))
        )
    }

    @Test
    fun `Adressen med kun landkode skal gi valideringfeil`() {
        assertEquals(
            SedTilPDLAdresse.Valideringsfeil("Ikke gyldig adresse, har kun landkode"),
            SedTilPDLAdresse(kodeverkClient).konverter("some kilde", Adresse(land = "DK"))
        )
    }

    private fun adresse(
        gate: String = "some gate",
        bygning: String = "some bygning",
        by: String = "some by",
        postnummer: String = "some postnummer",
        region: String = "some region",
        land: String = "NL"
    ) = Adresse(
        gate = gate,
        bygning = bygning,
        by = by,
        postnummer = postnummer,
        region = region,
        land = land,
        kontaktpersonadresse = null, // TODO hva er dette?
        datoforadresseendring = null, // er dette noe
        postadresse = null, // hva er dette?
        startdato = null,  // hva er dette?
        type = null,   // hva er dette?
        annen = null // hva er dette?
    )

    private fun endringsmeldingKontaktAdresse(
        kilde: String = "some kilde",
        gyldigFraOgMed: LocalDate? = LocalDate.now(),
        gyldigTilOgMed: LocalDate? = LocalDate.now().plusYears(1),
        coAdressenavn: String? = null,
        adresse: EndringsmeldingUtenlandskAdresse
    ) = EndringsmeldingKontaktAdresse(
        kilde = kilde,
        gyldigFraOgMed = gyldigFraOgMed,
        gyldigTilOgMed = gyldigTilOgMed,
        coAdressenavn = coAdressenavn,
        adresse = adresse
    )

    private fun endringsmeldingUtenlandskAdresse(
        adressenavnNummer: String? = null,
        bygningEtasjeLeilighet: String? = "some bygning",
        bySted: String? = "some by",
        landkode: String = "NLD",
        postboksNummerNavn: String? = null,
        postkode: String? = "some postnummer",
        regionDistriktOmraade: String? = "some region"
    ) = EndringsmeldingUtenlandskAdresse(
        adressenavnNummer = adressenavnNummer,
        bygningEtasjeLeilighet = bygningEtasjeLeilighet,
        bySted = bySted,
        landkode = landkode,
        postboksNummerNavn = postboksNummerNavn,
        postkode = postkode,
        regionDistriktOmraade = regionDistriktOmraade
    )


}

