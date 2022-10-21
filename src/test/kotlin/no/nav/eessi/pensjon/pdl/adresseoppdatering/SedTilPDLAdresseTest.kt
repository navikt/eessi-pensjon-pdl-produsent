package no.nav.eessi.pensjon.pdl.adresseoppdatering

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.model.sed.Adresse
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.kodeverk.LandkodeException
import no.nav.eessi.pensjon.models.EndringsmeldingKontaktAdresse
import no.nav.eessi.pensjon.models.EndringsmeldingUtenlandskAdresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskAdresse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate

class SedTilPDLAdresseTest {

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

        val pdlAdresse =
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

        assertEquals(pdlAdresse, SedTilPDLAdresse(kodeverkClient).konverter("kilde", sedAdresse))
    }

    @Test
    fun `Gateadresse med postboksadresse skal fylles ut i postboksNummerNavn`() {
        val sedAdresse = adresse(gate = "postboks 123")
        val pdlAdresse =
            endringsmeldingKontaktAdresse(
                adresse = endringsmeldingUtenlandskAdresse(
                    adressenavnNummer = null,
                    postboksNummerNavn = "postboks 123"
                )
            )

        assertEquals(pdlAdresse, SedTilPDLAdresse(kodeverkClient).konverter("some kilde", sedAdresse))
    }

    @ParameterizedTest
    @CsvSource("ukjent", "vet ikke"
    )
    fun `Gitt en adresse der by inneholder ukjent, saa gjoer vi ingen oppdatering`(ugyldigOrd: String){
        val ex = assertThrows<IllegalArgumentException> {
            SedTilPDLAdresse(kodeverkClient).konverter("some kilde", adresse(by = ugyldigOrd))
        }
        assertEquals("Ikke gyldig bySted: $ugyldigOrd", ex.message)
    }

    @Test
    fun `Gateadresse med ukjent skal gi valideringfeil`() {
        val ex = assertThrows<IllegalArgumentException> {
            SedTilPDLAdresse(kodeverkClient).konverter("some kilde", adresse(gate = "ukjent badeland strasse"))
        }
        assertEquals("Ikke gyldig adressenavnNummer: ukjent badeland strasse", ex.message)
    }

    @Test
    fun `regionDistriktOmraade med bare tall skal gi valideringfeil`() {
        val ex = assertThrows<IllegalArgumentException> {
            SedTilPDLAdresse(kodeverkClient).konverter("some kilde", adresse(region = "165483546"))
        }
        assertEquals("Ikke gyldig regionDistriktOmraade: 165483546", ex.message)
    }

    @Test
    fun `postkode som inneholder kun tegn saa skal det gi valideringfeil`() {
        val ex = assertThrows<IllegalArgumentException> {
            SedTilPDLAdresse(kodeverkClient).konverter("some kilde", adresse(postnummer = "***"))
        }
        assertEquals("Ikke gyldig postkode: ***", ex.message)
    }

    @Test
    fun `postkode er null skal gi valideringsfeil`() {
        val ex = assertThrows<IllegalArgumentException> {
            SedTilPDLAdresse(kodeverkClient).konverter("some kilde", adresse(postnummer = null))
        }
        assertEquals("Ikke gyldig postkode: null", ex.message)
    }

    @Test
    fun `bygningEtasjeLeilighet med ordet postboks skal gi valideringfeil`() {
        val ex = assertThrows<IllegalArgumentException> {
            SedTilPDLAdresse(kodeverkClient).konverter("some kilde", adresse(bygning = "postboks 321"))
        }
        assertEquals("Ikke gyldig bygningEtasjeLeilighet: postboks 321", ex.message)
    }

    @Test
    fun `Landkode som er null skal gi valideringfeil`() {
        val ex = assertThrows<IllegalArgumentException> {
            SedTilPDLAdresse(kodeverkClient).konverter("some kilde", Adresse(land = null))
        }
        assertEquals("Mangler landkode", ex.message)
    }

    @Test
    fun `Landkode som er ugyldig skal gi valideringfeil`() {
        every { kodeverkClient.finnLandkode("X") } .throws(LandkodeException(""))

        val ex = assertThrows<IllegalArgumentException> {
            SedTilPDLAdresse(kodeverkClient).konverter("some kilde", Adresse(land = "X"))
        }
        assertEquals("Ugyldig landkode: X", ex.message)
    }

    @Test
    fun `Adressen med kun landkode skal gi valideringfeil`() {
        val ex = assertThrows<IllegalArgumentException> {
            SedTilPDLAdresse(kodeverkClient).konverter("some kilde", Adresse(land = "DK"))
        }
        // Etter at vi innførte krav om postkode så slår denne til
        // før sjekken om at det finnes minst ett felt med verdi
        assertEquals("Ikke gyldig postkode: null", ex.message)
    }

    @Test
    fun `Adresse uten gatenavn og uten postboks gir valideringfeil`() {
        val ex = assertThrows<IllegalArgumentException> {
            SedTilPDLAdresse(kodeverkClient).konverter("some kilde", Adresse(postnummer = "1920", land = "DK"))
        }
        assertEquals("Ikke gyldig adresse, trenger enten adressenavnNummer eller postboksNummerNavn", ex.message)
    }

    @Test
    fun `Gitt en utlandsadresse i SED saa sjekker vi om den finnes i PDL`(){
        every { kodeverkClient.finnLandkode("SE") } returns "SWE"

        val adresse = Adresse(
            gate = "EddyRoad",
            bygning = "EddyHouse",
            by = "EddyCity",
            postnummer = "111",
            region = "Oslo",
            land ="SWE",
            kontaktpersonadresse = null,
        )
        val utenlandskAdresse = UtenlandskAdresse(
            adressenavnNummer = adresse.gate,
            landkode = "SE",
            postkode = adresse.postnummer,
            bySted = adresse.by,
            bygningEtasjeLeilighet  = adresse.bygning,
            regionDistriktOmraade = adresse.region
        )
        assertTrue(SedTilPDLAdresse(kodeverkClient).isUtenlandskAdresseISEDMatchMedAdresseIPDL(adresse, utenlandskAdresse))
    }

    @Test
    fun `Gitt en utlandsadresse i SED saa sjekker vi om den finnes i PDL - men vi har nulls fra SED`(){
        every { kodeverkClient.finnLandkode("SE") } returns "SWE"

        val adresse = Adresse(
            gate = "",
            bygning = null,
            by = null,
            postnummer = "111",
            region = null,
            land ="SWE",
            kontaktpersonadresse = null,
        )
        val utenlandskAdresse = UtenlandskAdresse(
            adressenavnNummer = "",
            landkode = "SE",
            postkode = adresse.postnummer,
            bySted = "",
            bygningEtasjeLeilighet  = "",
            regionDistriktOmraade = ""
        )
        assertTrue(SedTilPDLAdresse(kodeverkClient).isUtenlandskAdresseISEDMatchMedAdresseIPDL(adresse, utenlandskAdresse))
    }



    private fun adresse(
        gate: String = "some gate",
        bygning: String = "some bygning",
        by: String = "some by",
        postnummer: String? = "some postnummer",
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

