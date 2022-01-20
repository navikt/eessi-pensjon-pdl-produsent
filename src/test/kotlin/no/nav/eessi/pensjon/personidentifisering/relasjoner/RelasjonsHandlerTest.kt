package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.eux.model.sed.P2000
import no.nav.eessi.pensjon.eux.model.sed.RelasjonTilAvdod
import no.nav.eessi.pensjon.eux.model.sed.SED
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class RelasjonsHandlerTest : RelasjonTestBase() {

    @Test
    fun `leter igjennom Sed paa P_BUC_01 etter norsk personnr`() {
        val forventetFnr = SLAPP_SKILPADDE

        val sed = SED.generateSedToClass<P2000>(
            generateSED(
                SedType.P2000,
                forsikretFnr = forventetFnr,
                gjenlevFnr = null,
                gjenlevRelasjon = RelasjonTilAvdod.EKTEFELLE
            )
        )

        val actual = RelasjonsHandler.hentRelasjoner(
                listOf(Pair("kisudyrfgodjf", sed)),
                BucType.P_BUC_01
        )

        assertEquals(1, actual.size)
        assertEquals(forventetFnr, actual.first().fnr!!.value)
    }
}