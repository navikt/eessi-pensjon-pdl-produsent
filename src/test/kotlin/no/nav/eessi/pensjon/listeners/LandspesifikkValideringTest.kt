package no.nav.eessi.pensjon.listeners

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class LandspesifikkValideringTest {

    private val valdidering = LandspesifikkValidering()

    @ParameterizedTest
    @CsvSource(
        "BEL, 770113-123-12",
        "BGR, 770113-123-12",
        "FIN, 770113-123-12",
        "ISL, 770113-123-12",
        "DNK, 770113-123-12")
    fun `Gitt en  Belgisk UID som skal valideres mot landspesifikk formatering saa skal den returnere gyldig`(land : String, uid : String) {
        assertEquals(true, valdidering.validerLandsspesifikkUID(land, uid))
    }
}
