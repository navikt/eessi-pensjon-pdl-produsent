package no.nav.eessi.pensjon.eux

import no.nav.eessi.pensjon.eux.model.sed.Person
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class UtenlandskPersonIdentifiseringTest {

    @Test
    fun `Gitt at vi skal hente alle personer fra en sed så returnerer vi en liste over personer`() {

        val utenlandskPersonIdentifisering = UtenlandskPersonIdentifisering()
        val sed = SED.fromJsonToConcrete(javaClass.getResource("/eux/sed/P2100-PinDK-NAV.json")!!.readText())

        val result = utenlandskPersonIdentifisering.hentAllePersoner(sed)
        assertEquals(result.size, 7)

    }


    @Test
    fun `Gitt at en dansk uid finnes i sed så skal vi kunne hente og returnere denne`() {

        val utenlandskPersonIdentifisering = UtenlandskPersonIdentifisering()
        val sed = SED.fromJsonToConcrete(javaClass.getResource("/eux/sed/P2100-PinDK-NAV.json")!!.readText())

        val resultat = utenlandskPersonIdentifisering.hentAlleUtenlandskeIder(listOf(sed))

        assertEquals(resultat.size, 1)
        assertEquals(resultat.first().id, "130177-1234")
        assertEquals(resultat.first().land, "DK")

    }

}