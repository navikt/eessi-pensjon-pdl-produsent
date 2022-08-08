package no.nav.eessi.pensjon.models

import no.nav.eessi.pensjon.personoppslag.pdl.model.Endringstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Opplysningstype
import no.nav.eessi.pensjon.utils.mapAnyToJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class EndringsmeldingUIDTest {

    @Test
    fun mapEndringMledingTilJson() {

        val pdlEndringsOpplysninger = PdlEndringOpplysning(
            listOf(
                Personopplysninger(
                    endringstype = Endringstype.OPPRETT,
                    ident = "12345678910",
                    endringsmelding = EndringsmeldingUID(
                        identifikasjonsnummer = "770113-123-12",
                        utstederland = "BEL",
                        kilde = "Belgian institution"
                    ),
                    opplysningstype = Opplysningstype.UTENLANDSKIDENTIFIKASJONSNUMMER
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
                "      \"kilde\" : \"Belgian institution\",\n" +
                "      \"identifikasjonsnummer\" : \"770113-123-12\",\n" +
                "      \"utstederland\" : \"BEL\"\n" +
                "    },\n" +
                "    \"opplysningsId\" : null\n" +
                "  } ]\n" +
                "}"

        assertEquals(expected, json)
    }
}


