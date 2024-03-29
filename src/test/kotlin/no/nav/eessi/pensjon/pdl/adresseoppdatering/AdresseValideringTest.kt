package no.nav.eessi.pensjon.pdl.adresseoppdatering

import no.nav.eessi.pensjon.pdl.adresseoppdatering.AdresseValidering.erGyldigAdressenavnNummerEllerBygningEtg
import no.nav.eessi.pensjon.pdl.adresseoppdatering.AdresseValidering.erGyldigByStedEllerRegion
import no.nav.eessi.pensjon.pdl.adresseoppdatering.AdresseValidering.erGyldigPostKode
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource


/**
 * Regler for validering av adresse er hentet fra:
 * https://confluence.adeo.no/display/MOFO/OMR-330+Adresser+-+06+-+Produsent+adresser#
 */

internal class AdresseValideringTest {
    /**
     * Bygning feltet kan ikke inneholde ordene: "postboks", "postb.",  "postbox", "po.box" og "p.b."
     * ikke ord som "ukjent" "vet ikke"
     * må inneholde minst 1 bokstav (kan ikke bestå av kun tegn eller siffer)
     */

    @ParameterizedTest
    @CsvSource("postboks", "postb.", "po.box", "ukjent", "vet ikke", "1", "Unknown", "p.o.box")
    fun `UGYLDIGE Adressenavnnummer bygning eller etasje`(ugyldigVerdi: String) {
        assertFalse(erGyldigAdressenavnNummerEllerBygningEtg(ugyldigVerdi))
    }

    // CsvSource har issues med f eks whitespace i start/slutt av eksemplene så vi tester tegn for seg
    @Test
    fun `UGYLDIGE tegn i Adressenavnnummer bygning eller etasje`() {
        listOf(
            "\"RATNIEKI\"",
            "\tTabbedalen",
            "Tabbe\tdalen"
        ).forEach { ugyldigVerdi ->
            assertFalse(erGyldigAdressenavnNummerEllerBygningEtg(ugyldigVerdi), "$ugyldigVerdi burde IKKE godtas") }
    }

    @ParameterizedTest
    @CsvSource("Strada Principala 34 Rimetea", "Antonína Čermáka 2a 160 68 Prague", "å", "Åmål", "Brannfjell", "Postbanken")
    fun `GYLDIG Adressenavnnummer bygning eller etasje`(gyldigeVerdier: String) {
        assertTrue(erGyldigAdressenavnNummerEllerBygningEtg(gyldigeVerdier))
    }

    @ParameterizedTest
    @CsvSource("ukjent", "ukjenT", "vet ikke", "1", "1+", "1", "!")
    fun `UGYLDIGE postkoder`(ugyldigVerdi: String) {
        assertFalse(erGyldigByStedEllerRegion(ugyldigVerdi))
    }

    @ParameterizedTest
    @CsvSource("1a", "1a1a1", "München", "a", "Červená Řečice")
    fun `GYLDIGE by Sted eller region`(gyldigeVerdier: String) {
        assertTrue(erGyldigByStedEllerRegion(gyldigeVerdier))
    }

    /**
     * Validering postkode:
     * ikke ord som "ukjent" "vet ikke".
     * må inneholde minst en bokstav eller ett tall (kan ikke inneholde kun andre tegn)
     */

    @ParameterizedTest
    @CsvSource("ukjent", "Ukjent", "UKJENT", "vet ikke", "+", "!+", "\" \"")
    fun `UGYLDIGE postkoder i adresse`(ugyldigPostkode: String) {
        assertFalse(erGyldigPostKode(ugyldigPostkode))
    }

    @ParameterizedTest
    @CsvSource("a", "1", "1a", "a1", "a1sdkuryiev65", "a1-1", "a1-1 dfuigh 35", "1 ")
    fun `GYLDIG postkode`(gyldigPostkode: String) {
        assertTrue(erGyldigPostKode(gyldigPostkode))
    }

}