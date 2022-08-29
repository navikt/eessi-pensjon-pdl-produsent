package no.nav.eessi.pensjon.pdl.adresseoppdatering

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.model.sed.Adresse
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.models.EndringsmeldingKontaktAdresse
import no.nav.eessi.pensjon.models.EndringsmeldingUtenlandskAdresse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SedTilPdlAdresseTest {

    var kodeverkClient: KodeverkClient = mockk(relaxed = true)

    @BeforeEach
    fun setup() {
        every { kodeverkClient.finnLandkode("DKK") } returns "DK"
        every { kodeverkClient.finnLandkode("DK") } returns "DKK"
    }

    @Test
    fun `konvertering av gateadresse`(){
        val sedAdresse = Adresse(
            gate = "gate",
            bygning = "bygning",
            by = "by",
            postnummer = "postnummer",
            region = "region",
            land = "DK",
            kontaktpersonadresse = null, // TODO hva er dette?
            datoforadresseendring = null, // er dette noe
            postadresse = null, // hva er dette?
            startdato = null,  // hva er dette?
            type = null,   // hva er dette?
            annen = null // hva er dette?
        )

        val pdlAdresse = EndringsmeldingKontaktAdresse(
            kilde = "kilde",
            gyldigFraOgMed = LocalDate.now(),
            gyldigTilOgMed = LocalDate.now().plusYears(1),
            coAdressenavn = null,
            adresse = EndringsmeldingUtenlandskAdresse(
                adressenavnNummer = "gate",
                bygningEtasjeLeilighet = "bygning",
                bySted = "by",
                landkode = "DKK",
                postkode = "postnummer",
                regionDistriktOmraade = "region"
            )
        )

        assertEquals(pdlAdresse, SedTilPDLAdresse(kodeverkClient).konverter("kilde", sedAdresse))
    }
}

