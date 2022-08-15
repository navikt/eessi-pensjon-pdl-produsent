package no.nav.eessi.pensjon.pdl.adresseoppdatering

import io.mockk.mockk
import no.nav.eessi.pensjon.models.SedHendelseModel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class AdresseoppdateringTest {

    @Test
    fun `Gitt SED uten utenlandsadresse, ingen oppdatering`() {
        val mockSedHendelse: SedHendelseModel = mockk(relaxed = true)
        val adresseoppdatering = Adresseoppdatering(mockk(), mockk(), mockk(), mockk())
        val result = adresseoppdatering.oppdaterUtenlandskKontaktadresse(mockSedHendelse)
        assertFalse(result)
    }

}
