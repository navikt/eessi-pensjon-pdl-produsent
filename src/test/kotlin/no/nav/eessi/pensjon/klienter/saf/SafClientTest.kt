package no.nav.eessi.pensjon.klienter.saf

import no.nav.eessi.pensjon.utils.mapAnyToJson
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.Test

class SafClientTest {
    @Test
    fun `Serdes`() {
        val hentMetadataResponse = HentMetadataResponse(
            data = Data(
                dokumentoversiktBruker = DokumentoversiktBruker(
                    journalposter = listOf(
                        Journalpost(
                            tilleggsopplysninger = listOf(mapOf(Pair("nokkel", "verdi"))),
                            journalpostId = "",
                            datoOpprettet = "",
                            tittel = "",
                            journalfoerendeEnhet = "",
                            tema = "",
                            dokumenter = null,
                            behandlingstema = ""
                        )
                    )
                )
            )
        )

        mapAnyToJson(hentMetadataResponse)
    }

    @Test
    fun `desSer`() {

        val jsonHentMetadataResponse = """
            {
              "data" : {
                "dokumentoversiktBruker" : {
                  "journalposter" : [ {
                    "tilleggsopplysninger" : [ {
                      "nokkel" : "verdi"
                    } ],
                    "journalpostId" : "",
                    "datoOpprettet" : "",
                    "tittel" : "",
                    "journalfoerendeEnhet" : "",
                    "tema" : "",
                    "dokumenter" : null,
                    "behandlingstema" : ""
                  } ]
                }
              }
            }
        """.trimIndent()

        mapJsonToAny<HentMetadataResponse>(jsonHentMetadataResponse)
    }

}