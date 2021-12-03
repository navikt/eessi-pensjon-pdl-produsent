package no.nav.eessi.pensjon.models

import no.nav.eessi.pensjon.json.mapAnyToJson
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class EndringsmeldingTest {


    @Test
    fun mapEndringMledingTilJson() {

        val pdlEndringsOpplysninger = PdlEndringOpplysning(
            listOf(
                Personopplysninger(
                    ident = "12345678910",
                    endringsmelding = Endringsmelding(
                        identifikasjonsnummer = "770113-123-12",
                        utstederland = "BEL",
                        kilde = "Belgian institution"
                    )
                )
            )
        )
        val json = mapAnyToJson(pdlEndringsOpplysninger)
        val expected = "{\n" +
                "  \"personopplysninger\" : [ {\n" +
                "    \"endringstype\" : \"OPPRETT\",\n" +
                "    \"ident\" : \"12345678910\",\n" +
                "    \"opplysningstype\" : \"UTENLANDSKIDENTIFIKASJONSNUMMER\",\n" +
                "    \"endringsmelding\" : {\n" +
                "      \"@type\" : \"UTENLANDSKIDENTIFIKASJONSNUMMER\",\n" +
                "      \"identifikasjonsnummer\" : \"770113-123-12\",\n" +
                "      \"utstederland\" : \"BEL\",\n" +
                "      \"kilde\" : \"Belgian institution\"\n" +
                "    },\n" +
                "    \"opplysningsId\" : null\n" +
                "  } ]\n" +
                "}"

        assertEquals(expected, json)
    }
}


