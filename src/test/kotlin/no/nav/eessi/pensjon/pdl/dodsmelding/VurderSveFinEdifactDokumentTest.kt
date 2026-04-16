package no.nav.eessi.pensjon.pdl.dodsmelding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VurderSveFinEdifactDokumentTest {

    private val tolk = VurderSveFinEdifactDokument()

    @Test
    fun `vurderEditfactDokument gir forventede felter for gyldig edifact`() {
        val resultat = tolk.vurderEditfactDokument(edifactDokForSveFin())

        assertEquals("SESFAE5PC", resultat?.avsender)
        assertEquals("NORTVE5LA", resultat?.mottaker)
        assertEquals("512", resultat?.meldingstype)
        assertEquals("1112190988", resultat?.referanse)
        assertEquals("SE", resultat?.avsenderLand)
        assertEquals("NO", resultat?.mottakerLand)
        assertEquals("19350951", resultat?.fodselsdato)
        assertTrue(resultat?.erSveFin == true)
    }

    private fun edifactDokForSveFin(): String {
        return """
            UNA:+.? 'UNB+UNOC:1+SESFAE5PC+NORTVE5LA+153121:2145+1222035112521'UNH+026030001+
            SSREGW:D:94B:UN'BGM+512+1112190988'DTM+137:20251121:102'GIS+1'NAD+FR+RFV++FORSAK
            RINGSKASSAN+++++SE'GIR+903+6655117099:RN'NAD+MR+RTV++RIKSTRYGDEVERKET+++++NO'GIR
            +903+01445517022:RN'PNA+SIP++2+1+1:FOYKE++2:USTABIL'NAT+1+NO'ADR+1::1+1:FREDSGAT
            AN 1+KARLSTAD+61225+NO'DTM+329:19350951:102'PDI+2+3'UNT+14+052000101'UNZ+15235+5
            122225132121'
        """.trimIndent()
    }
}
