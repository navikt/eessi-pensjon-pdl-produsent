package no.nav.eessi.pensjon.listeners

import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.models.SedHendelseModel
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

internal class GyldigeHendelserTest {


    @Test
    fun `Mottatt hendelse som IKKE er R_BUC_02, H_BUC_07, eller sektorkode P er ugyldig`() {
        val hendelse = createDummy("", BucType.P_BUC_01)

        assertFalse(GyldigeHendelser.erGyldigInnkommetSed(hendelse))
    }

    @Test
    fun `Mottatt hendelse som mangler BucType`() {
        val hendelse = createDummy("", null)
        assertFalse(GyldigeHendelser.erGyldigInnkommetSed(hendelse))
    }

    @Test
    fun `mottatt hendelse som mangler BucType`() {
        val hendelse = createDummy("", null)

        assertFalse(GyldigeHendelser.erGyldigInnkommetSed(hendelse))
    }

    private fun createDummy(sektor: String, bucType: BucType?) =
            SedHendelseModel(sektorKode = sektor, bucType = bucType, rinaSakId = "12345", rinaDokumentId = "654634", rinaDokumentVersjon = "1")
}
