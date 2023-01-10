package no.nav.eessi.pensjon.pdl.validering

import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.eux.model.buc.BucType.*
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

internal class RelevanteHendelserTest {

    @Test
    fun `Mottatt hendelse som IKKE er R_BUC_02, H_BUC_07, eller sektorkode P er er ikke relevant`() {
        val hendelse = createDummy(bucType = P_BUC_01)
        assertFalse(erRelevantForEESSIPensjon(hendelse))
    }

    @Test
    fun `Mottatt hendelse som mangler BucType er ikke relevant`() {
        val hendelse = createDummy()
        assertFalse(erRelevantForEESSIPensjon(hendelse))
    }

    @Test
    fun `mottatt hendelse som mangler BucType er ikke relevant`() {
        val hendelse = createDummy()
        assertFalse(erRelevantForEESSIPensjon(hendelse))
    }

    private fun createDummy(sektor: String = "", bucType: BucType? = null) =
            SedHendelse(sektorKode = sektor, bucType = bucType, rinaSakId = "12345", rinaDokumentId = "654634", rinaDokumentVersjon = "1")
}
