package no.nav.eessi.pensjon.eux

import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.eux.model.sed.SED
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class UtenlandskPersonIdentifiseringTest {

    private val utenlandskPersonIdentifisering = UtenlandskPersonIdentifisering()

    @Test
    fun `Gitt at en dansk uid finnes i sed så skal vi kunne hente og returnere denne`() {

        val sed = SED.fromJsonToConcrete(javaClass.getResource("/eux/sed/P2100-PinDK-NAV.json")!!.readText())

        val resultat = utenlandskPersonIdentifisering.finnAlleUtenlandskeIDerIMottatteSed(listOf(Pair(mockForenkledSed(SedType.P2100), sed)))

        assertEquals(2, resultat.size)
        assertEquals(resultat.first().id, "130177-1234")
        assertEquals(resultat.first().land, "DK")

    }

    private fun mockForenkledSed(sedType: SedType): ForenkletSED = ForenkletSED("1231231", sedType, SedStatus.RECEIVED)

}