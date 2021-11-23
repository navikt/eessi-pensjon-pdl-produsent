package no.nav.eessi.pensjon.listeners

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

internal class LandspesifikkValideringTest {

    private val valdidering = LandspesifikkValidering()

    @ParameterizedTest
    @ValueSource(strings = ["BEL", "BGR", "FIN", "ISL", "DNK"])
    fun `Gitt en  Belgisk UID som skal valideres mot landspesifikk formatering saa skal den returnere gyldig`(land : String) {
        assertEquals(true, valdidering.validerLandsspesifikkUID(land, "770113-123-12"))
    }
}
