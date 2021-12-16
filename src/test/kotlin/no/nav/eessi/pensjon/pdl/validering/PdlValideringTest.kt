package no.nav.eessi.pensjon.pdl.validering

import io.mockk.mockk
import no.nav.eessi.pensjon.eux.UtenlandskId
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse

internal class PdlValideringTest {
    private val kodeverkClient = mockk<KodeverkClient>(relaxed = true)


    @Test
    fun `Gitt en ny uid naar avsenderland og uidland ikke er det samme saa returner true`() {
        val pdlValidering = PdlValidering(kodeverkClient)
        val identifisertPerson = UtenlandskId("11067122781", "DE")

        assertTrue(pdlValidering.erUidLandAnnetEnnAvsenderLand(identifisertPerson, "SE"))
    }

    @Test
    fun `Gitt en ny uid naar avsenderland og uidland er det samme saa returner false`() {
        val pdlValidering = PdlValidering(kodeverkClient)
        val identifisertPerson = UtenlandskId("11067122781", "DE")

        assertFalse (pdlValidering.erUidLandAnnetEnnAvsenderLand(identifisertPerson, "DE"))
    }
}