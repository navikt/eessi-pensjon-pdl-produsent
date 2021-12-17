package no.nav.eessi.pensjon.eux

import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.sed.SED
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class UtenlandskPersonIdentifiseringTest {

    private val utenlandskPersonIdentifisering = UtenlandskPersonIdentifisering()

    @Test
    fun `Gitt at vi skal hente alle personer fra en sed så returnerer vi en liste over personer`() {

        val sed = SED.fromJsonToConcrete(javaClass.getResource("/eux/sed/P2100-PinDK-NAV.json")!!.readText())

        val result = utenlandskPersonIdentifisering.hentAllePersoner(sed)
        assertEquals(3, result.size)

    }

    @Test
    fun `Gitt at vi skal hente alle personer fra en tom sed så returnerer vi en tom liste`() {

        val s = SED(SedType.P2100)

        println(utenlandskPersonIdentifisering.hentAllePersoner(s))
        assertEquals(0, utenlandskPersonIdentifisering.hentAllePersoner(s).size)
    }

    @Test
    fun `Gitt at en dansk uid finnes i sed så skal vi kunne hente og returnere denne`() {

        val sed = SED.fromJsonToConcrete(javaClass.getResource("/eux/sed/P2100-PinDK-NAV.json")!!.readText())

        val resultat = utenlandskPersonIdentifisering.hentAlleUtenlandskeIder(listOf(sed))

        assertEquals(1, resultat.size)
        assertEquals(resultat.first().id, "130177-1234")
        assertEquals(resultat.first().land, "DK")

    }

    @Test
    fun `Gitt en P7000 med personer når vi henter personer fra sed så returneres alle spesifikke personer med pin`() {

        val p7000Json = javaClass.getResource("/eux/sed/P7000-NAV.json")!!.readText()
        val p7000 = SED.fromJsonToConcrete(p7000Json)
        val resultat = utenlandskPersonIdentifisering.hentAllePersoner(p7000)

        assertEquals(2, resultat.size)

    }

    @Test
    fun `Gitt en P5000 med personer når vi henter personer fra sed så returneres alle spesifikke personer med pin`() {

        val p5000 = SED.fromJsonToConcrete(javaClass.getResource("/eux/sed/P7000-NAV.json")!!.readText())
        assertEquals(2, utenlandskPersonIdentifisering.hentAllePersoner(p5000).size)

    }

    @Test
    fun `Gitt en P15000 med personer når vi henter personer fra sed så returneres alle spesifikke personer med pin`() {

        val p15000 = SED.fromJsonToConcrete(javaClass.getResource("/eux/sed/P15000-NAV.json")!!.readText())
        assertEquals(2, utenlandskPersonIdentifisering.hentAllePersoner(p15000).size)

    }

    @Test
    fun `Gitt en P15000 med personer uten pin når vi henter personer fra sed så returneres tom liste`() {

        val p15000 = SED.fromJsonToConcrete(javaClass.getResource("/eux/sed/P15000-UtenPin-NAV.json")!!.readText())
        assertEquals(0, utenlandskPersonIdentifisering.hentAllePersoner(p15000).size)

    }

    @Test
    fun `Gitt en P2200 med personer inklusive barn når vi henter personer fra sed så returneres alle spesifikke personer med pin`() {

        val p2200 = SED.fromJsonToConcrete(javaClass.getResource("/eux/sed/P2200-MedFamilie-NAV.json")!!.readText())
        assertEquals(5, utenlandskPersonIdentifisering.hentAllePersoner(p2200).size)

    }

}