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
        "BGR, 12345678901, true" ,
        "FIN, 770113-123-12, true",
        "ISL, 770113-123-12, true",
        "DNK, 770113-123-12, true")
    fun `Gitt en  Belgisk UID som skal valideres mot landspesifikk formatering saa skal den returnere gyldig`(land : String, uid : String, check: Boolean) {
        assertEquals(check, valdidering.validerLandsspesifikkUID(land, uid))
    }
}
