package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.P8000
import no.nav.eessi.pensjon.eux.model.sed.RelasjonTilAvdod
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.models.BucType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class RelasjonsHandlerTest : RelasjonTestBase() {

    @Test
    fun `leter igjennom Sed paa P_BUC_01 etter norsk personnr`() {
        val forventetFnr = SLAPP_SKILPADDE

        val actual = RelasjonsHandler.hentRelasjoner(
                    SED.generateSedToClass<P8000>(
                        generateSED(
                            SedType.P2000,
                            forsikretFnr = forventetFnr,
                            gjenlevFnr = null,
                            gjenlevRelasjon = RelasjonTilAvdod.EKTEFELLE
                        )
                    )
                    ,"213123"
                    ,BucType.P_BUC_01
        )

        assertEquals(1, actual.size)
        assertEquals(SedType.P2000, actual.firstOrNull { it.sedType == SedType.P2000 }?.sedType)
        assertEquals(forventetFnr, actual.firstOrNull{ it.sedType == SedType.P2000}?.fnr?.value)
    }

}