package no.nav.eessi.pensjon.pdl.validering

import io.mockk.mockk
import no.nav.eessi.pensjon.eux.UtenlandskId
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PdlValideringTest {
    private val kodeverkClient = mockk<KodeverkClient>(relaxed = true)

    private val pdlValidering = PdlValidering(kodeverkClient)

    @Test
    fun `Gitt en ny uid naar avsenderland og uidland ikke er det samme saa returner false`() {

        val identifisertPerson = UtenlandskId("11067122781", "DE")
        assertFalse(pdlValidering.avsenderLandHarVerdiOgErSammeSomUidLand(identifisertPerson, "SE"))
    }

    @Test
    fun `Gitt en ny uid naar avsenderland og uidland er det samme saa returner true`() {

        val identifisertPerson = UtenlandskId("11067122781", "DE")
        assertTrue(pdlValidering.avsenderLandHarVerdiOgErSammeSomUidLand(identifisertPerson, "DE"))
    }
}