package no.nav.eessi.pensjon.pdl.adresseoppdatering

import no.nav.eessi.pensjon.pdl.validering.AdresseValidering
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test


/**
 * Regler for validering av adresse er hentet fra:
 * https://confluence.adeo.no/display/MOFO/OMR-330+Adresser+-+06+-+Produsent+adresser#
 */

internal class AdresseValideringTest() {


    @Test
    fun `Gitt en sed med en tysk bostedsadresse fra en institusjon i Tyskland og den ikke finnes i PDL saa skal PDL oppdateres med ny bostedsadresse`() {
        //TODO
    }

    @Test
    fun `Gitt en sed som inneholder en tysk bostedsadresse fra en institusjon i Tyskland og den ikke finnes i PDL saa skal PDL oppdateres med ny bostedsadresse og institusjonen skal registreres som kilde for adressen`() {
        //TODO
    }

    @Test
    fun `Gitt en sed med en adresse for en avdod person saa skal adressen oppdateres i PDL som kontaktadresse`() {
        //TODO
    }

    @Test
    fun `Gitt at en person har adressebeskyttelse fortrolig eller strengt fortrolig saa skal adressen ikke registreres`() {
        //TODO
    }

    @Test
    fun `Gitt at en person har adressebeskyttelse fortrolig utland saa skal adressen registreres`() {
        //TODO
    }

    @Test
    fun `Gitt en kontaktadresse som skal oppdateres i PDL saa skal dagens dato i gyldig form og dato ett aar frem i tid i gyldig tom dato`() {   //Gyldighetsperiode
        //TODO
    }

    @Test
    fun `Gitt en adresse som skal valideres saa maa adressen inneholde minst landkode og ett annet felt`() {
        //TODO
    }

    @Test
    fun `Gitt en adresse som skal valideres som mangler landkode saa skal ikke adressen registreres`() {
        //TODO
    }


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