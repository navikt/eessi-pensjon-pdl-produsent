package no.nav.eessi.pensjon.models

import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode

internal class SedHendelseModelSerdeTest {

    @Test
    fun `Sjekk at serialisering virker`() {
        val model = SedHendelseModel(rinaSakId = "123456", rinaDokumentId = "1234", sektorKode = "R", bucType = BucType.R_BUC_02, rinaDokumentVersjon = "1")
        val serialized = model.toJson()

        val result = SedHendelseModel.fromJson(serialized)

        assertEquals(model, result)
    }

    @Test
    fun `Sjekker at deserialisering gir riktig verdi`() {
        val json = """{
            "id" : 0,
            "sedId" : null,
            "sektorKode" : "R",
            "bucType" : "P_BUC_02",
            "rinaSakId" : "123456",
            "avsenderId" : null,
            "avsenderNavn" : null,
            "avsenderLand" : null,
            "mottakerId" : null,
            "mottakerNavn" : null,
            "mottakerLand" : null,
            "rinaDokumentId" : "1234",
            "rinaDokumentVersjon" : "1",
            "sedType" : null
        }""".trimMargin()

        val model = SedHendelseModel.fromJson(json)

        val result = model.toJson()
        JSONAssert.assertEquals(json, result, JSONCompareMode.LENIENT)
    }

    @Test
    fun `Deserialisering med ugyldig bucType skal gi bucType null`() {
        val json = """{
            "id" : 0,
            "sedId" : null,
            "sektorKode" : "R",
            "bucType" : "FB_BUC_01",
            "rinaSakId" : "123456",
            "avsenderId" : null,
            "avsenderNavn" : null,
            "avsenderLand" : null,
            "mottakerId" : null,
            "mottakerNavn" : null,
            "mottakerLand" : null,
            "rinaDokumentId" : "1234",
            "rinaDokumentVersjon" : "1",
            "sedType" : null,
            "navBruker" : null
        }""".trimMargin()

        val model = SedHendelseModel.fromJson(json)

        assertEquals("R", model.sektorKode)
        assertNull(model.bucType)
    }

    @Test
    fun `Deserialisering med gyldig rinasakid`() {
        val json = """{
            "id" : 0,
            "sedId" : null,
            "sektorKode" : "R",
            "bucType" : "FB_BUC_01",
            "rinaSakId" : "123456",
            "avsenderId" : null,
            "avsenderNavn" : null,
            "avsenderLand" : null,
            "mottakerId" : null,
            "mottakerNavn" : null,
            "mottakerLand" : null,
            "rinaDokumentId" : "1234",
            "rinaDokumentVersjon" : "1",
            "sedType" : null,
            "navBruker" : "22117320034"
        }""".trimMargin()

        val model = SedHendelseModel.fromJson(json)

        assertEquals("123456", model.rinaSakId)
    }

}
