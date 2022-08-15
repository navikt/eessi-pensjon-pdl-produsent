package no.nav.eessi.pensjon.pdl.adresseoppdatering

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.sed.Adresse
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.klienter.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.models.SedHendelseModel
import no.nav.eessi.pensjon.pdl.filtrering.PdlFiltrering
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import no.nav.eessi.pensjon.personoppslag.pdl.model.Kontaktadresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskAdresse
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class AdresseoppdateringTest {

    var pdlService: PersonidentifiseringService = mockk()
    var euxService: EuxService = mockk(relaxed = true)
    var kodeverkClient: KodeverkClient = mockk(relaxed = true)

    var pdlFiltrering: PdlFiltrering = PdlFiltrering(kodeverkClient)

    @Test
    fun `Gitt SED uten utenlandsadresse, ingen oppdatering`() {
        val mockSedHendelse: SedHendelseModel = mockk(relaxed = true)
        val adresseoppdatering = Adresseoppdatering(mockk(), mockk(), mockk(), mockk())
        val result = adresseoppdatering.oppdaterUtenlandskKontaktadresse(mockSedHendelse)
        assertFalse(result)
    }

    @Test
    fun `Gitt SED med gyldig utlandsadresse, og denne finnes i PDL, saa skal det sendes oppdatering`() {
        val json = """{
            "id" : 0,
            "sektorKode" : "P",
            "bucType" : "P_BUC_02",
            "rinaSakId" : "123456",
            "avsenderNavn" : null,
            "avsenderLand" : null,
            "mottakerNavn" : null,
            "mottakerLand" : null,
            "rinaDokumentId" : "1234",
            "rinaDokumentVersjon" : "1",
            "sedType" : "P2100",
            "navBruker" : "22117320034"
        }""".trimMargin()

        val model = SedHendelseModel.fromJson(json)

        val adresseFraSED = Adresse(
            gate = "EddyRoad",
            bygning = "EddyHouse",
            by = "EddyCity",
            postnummer = "111",
            postkode = "666",
            region = "Oslo",
            land ="SWE",
            kontaktpersonadresse = null,
        )
        val adresseFraPDL = UtenlandskAdresse(
            adressenavnNummer = adresseFraSED.gate,
            landkode = "SE",
            postkode = adresseFraSED.postkode,
            bySted = adresseFraSED.by,
            bygningEtasjeLeilighet  = adresseFraSED.bygning,
            regionDistriktOmraade = adresseFraSED.region
        )

        val mockedPerson: IdentifisertPerson = mockk()
        var mockedKontaktAdresse: Kontaktadresse = mockk(relaxed = true)
        var mockedSed: SED = mockk(relaxed = true)

        every { mockedSed.nav?.bruker?.adresse } returns adresseFraSED
        every { kodeverkClient.finnLandkode("SE") } returns "SWE"
        every { mockedPerson.kontaktAdresse } returns mockedKontaktAdresse
        every { pdlService.hentIdentifisertePersoner(any(), any()) } returns listOf(mockedPerson)
        every { euxService.alleGyldigeSEDForBuc(eq("123456")) } returns listOf(Pair(mockk(), mockedSed))
        every { mockedKontaktAdresse.utenlandskAdresse } returns adresseFraPDL

        val adresseoppdatering = Adresseoppdatering(pdlService, euxService, mockk(), pdlFiltrering)
        adresseoppdatering.oppdaterUtenlandskKontaktadresse(model)

        verify(atLeast = 1) { pdlService.hentIdentifisertePersoner(any(), any()) }
    }
}
