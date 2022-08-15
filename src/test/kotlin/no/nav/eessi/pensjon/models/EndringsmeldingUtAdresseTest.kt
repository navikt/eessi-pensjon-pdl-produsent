package no.nav.eessi.pensjon.models

import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskAdresse
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.typeRefs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class EndringsmeldingUtAdresseTest() {

    private val utenlandskAdresse = UtenlandskAdresse(
        landkode = "NO",
        adressenavnNummer = "234234",
        postkode = "3443",
        bySted = "UTLANDBY",
        bygningEtasjeLeilighet = "bygningtest",
        postboksNummerNavn = "1111Oslo",
        regionDistriktOmraade = "Oslo"
    )

    @Test
    fun `Sjekk at serialisering virker for EndringsmeldingUtAdresse`() {
        val model = EndringsmeldingUtAdresse(
            gyldigFraOgMed = LocalDate.of(2000, 10, 1),
            gyldigTilOgMed = LocalDate.of(2001, 11, 2),
            coAdressenavn = "En eller annen vei",
            adresse = utenlandskAdresse,
            kilde = "TEST",
        )

        val expected = """
            {
              "@type" : "KONTAKTADRESSE",
              "kilde" : "TEST",
              "gyldigFraOgMed" : "2000-10-01",
              "gylidgTilOgMed" : "2001-11-02",
              "coAdressenavn" : "En eller annen vei",
              "adresse" : {
                "adressenavnNummer" : "234234",
                "bySted" : "UTLANDBY",
                "bygningEtasjeLeilighet" : "bygningtest",
                "landkode" : "NO",
                "postboksNummerNavn" : "1111Oslo",
                "postkode" : "3443",
                "regionDistriktOmraade" : "Oslo"
              }
            }
        """.trimIndent()
        assertEquals(expected, model.toJson().also { println(it) })
        assertEquals(model, mapJsonToAny<EndringsmeldingUtAdresse>(model.toJson(), typeRefs()))
    }
}



