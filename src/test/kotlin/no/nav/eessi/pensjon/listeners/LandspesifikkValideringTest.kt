package no.nav.eessi.pensjon.listeners

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class LandspesifikkValideringTest {

    private val valdidering = LandspesifikkValidering()

    @ParameterizedTest
    @CsvSource(
        "BEL, 770113-123-12, true",
        "BEL, 770113-12312, false",
        "BGR, 1234567890, true" ,
        "BGR, 123456789012, false" ,
        "BGR, 770113-12312, false",
        "FIN, 130177A636D, true",
        "FIN, 130177-308T, true",
        "FIN, 130177-5800, true",
        "FIN, 130177A5800, true",
        "FIN, 130177A636D, true",
        "ISL, 770113-123-12, true",
        "DNK, 770113-123-12, true")
    fun `Gitt en  Belgisk UID som skal valideres mot landspesifikk formatering saa skal den returnere gyldig`(land : String, uid : String, check: Boolean) {
        assertEquals(check, valdidering.validerLandsspesifikkUID(land, uid))
    }
}
