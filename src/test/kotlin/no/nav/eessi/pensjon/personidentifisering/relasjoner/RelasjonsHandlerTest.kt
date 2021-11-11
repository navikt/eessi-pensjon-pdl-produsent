package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.P2000
import no.nav.eessi.pensjon.eux.model.sed.P8000
import no.nav.eessi.pensjon.eux.model.sed.PinItem
import no.nav.eessi.pensjon.eux.model.sed.RelasjonTilAvdod
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import no.nav.eessi.pensjon.personidentifisering.SEDPersonRelasjon
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class RelasjonsHandlerTest : RelasjonTestBase() {

    @Test
    fun `leter igjennom beste Sed på P_BUC_01 etter norsk personnr`() {
        val forventetFnr = SLAPP_SKILPADDE

        val actual = RelasjonsHandler.hentRelasjoner(
            listOf(
                // P2100 som mangler norsk fnr
                Pair(
                    "3123131",
                    SED.generateSedToClass<P8000>(
                        generateSED(
                            SedType.P8000,
                            forsikretFnr = null,
                            gjenlevFnr = null,
                            gjenlevRelasjon = RelasjonTilAvdod.EKTEFELLE
                        )
                    )
                ), Pair("3123134", SED.generateSedToClass<P2000>(generateSED(SedType.P2000, forsikretFnr = forventetFnr)))
            ), BucType.P_BUC_01
        )

        val sok = createSokKritere()
        val expected = setOf(
            SEDPersonRelasjon(
                Fodselsnummer.fra(forventetFnr),
                emptyList<PinItem>(),
                relasjon = Relasjon.FORSIKRET,
                sedType = SedType.P2000,
                saktype = Saktype.ALDER
            )
        )
        assertEquals(2, actual.size)
        assertTrue(actual.containsAll(expected))
        assertEquals(expected.first(), actual.firstOrNull { it.sedType == SedType.P2000 })
    }

}