package no.nav.eessi.pensjon.pdl.validering

import no.nav.eessi.pensjon.pdl.validering.AdresseValidering
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test


/**
 * Regler for validering av adresse er hentet fra:
 * https://confluence.adeo.no/display/MOFO/OMR-330+Adresser+-+06+-+Produsent+adresser#
 */

internal class AdresseValideringTest() {
    //    Bygning feltet kan ikke inneholde ordene: "postboks", "postb.",  "postbox", "po.box" og "p.b."
    //    ikke ord som "ukjent" "vet ikke", "nn"
    //    må inneholde minst 1 bokstav (kan ikke bestå av kun tegn eller siffer)

    @Test
    fun `Gitt en adresse der validering av by eller sted gir false saa er det ikke en gyldig adresse`() {
        assertFalse(AdresseValidering().erGyldigAdressenavnNummerEllerBygningEtg("postboks"))
        assertFalse(AdresseValidering().erGyldigAdressenavnNummerEllerBygningEtg("postb."))
        assertFalse(AdresseValidering().erGyldigAdressenavnNummerEllerBygningEtg("po.box"))
        assertFalse(AdresseValidering().erGyldigAdressenavnNummerEllerBygningEtg("ukjent"))
        assertFalse(AdresseValidering().erGyldigAdressenavnNummerEllerBygningEtg("vet ikke"))
        assertFalse(AdresseValidering().erGyldigAdressenavnNummerEllerBygningEtg("nn"))
        assertFalse(AdresseValidering().erGyldigAdressenavnNummerEllerBygningEtg("1"))

        assertTrue(AdresseValidering().erGyldigAdressenavnNummerEllerBygningEtg("a"))
    }


    @Test
    fun `Gitt en adresse der validering avpostkode gir false saa er det ikke en gyldig adresse`() {
        assertFalse(AdresseValidering().erGyldigByStedEllerRegion("ukjent"))
        assertFalse(AdresseValidering().erGyldigByStedEllerRegion("vet ikke"))
        assertFalse(AdresseValidering().erGyldigByStedEllerRegion("nn"))

        assertFalse(AdresseValidering().erGyldigByStedEllerRegion("1"))
        assertFalse(AdresseValidering().erGyldigByStedEllerRegion("1 "))
        assertFalse(AdresseValidering().erGyldigByStedEllerRegion("1+"))

        assertTrue(AdresseValidering().erGyldigByStedEllerRegion("a"))
        assertTrue(AdresseValidering().erGyldigByStedEllerRegion("1a"))
        assertTrue(AdresseValidering().erGyldigByStedEllerRegion("1a1a1"))

    }

    //  Validering postkode:
    //  ikke ord som "ukjent" "vet ikke". "nn"
    //  må inneholde minst en bokstav eller ett tall (kan ikke inneholde kun andre tegn)

    @Test
    fun `Gitt en adresse der validering av postkode gir false saa er det ikke en gyldig adresse`() {
        assertFalse(AdresseValidering().erGyldigPostKode("ukjent"))
        assertFalse(AdresseValidering().erGyldigPostKode("vet ikke"))
        assertFalse(AdresseValidering().erGyldigPostKode("nn"))
        assertFalse(AdresseValidering().erGyldigPostKode("+"))
        assertFalse(AdresseValidering().erGyldigPostKode(" "))

        assertTrue(AdresseValidering().erGyldigPostKode("a"))
        assertTrue(AdresseValidering().erGyldigPostKode("1"))
        assertTrue(AdresseValidering().erGyldigPostKode("1a"))
        assertTrue(AdresseValidering().erGyldigPostKode("a1"))
        assertTrue(AdresseValidering().erGyldigPostKode("a1sdkuryiev65"))
        assertTrue(AdresseValidering().erGyldigPostKode("a1-1"))
        assertTrue(AdresseValidering().erGyldigPostKode("a1-1 dfuigh 35"))
    }


}