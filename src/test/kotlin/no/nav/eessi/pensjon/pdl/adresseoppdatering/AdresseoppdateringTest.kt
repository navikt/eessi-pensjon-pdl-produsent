package no.nav.eessi.pensjon.pdl.adresseoppdatering

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.sed.Adresse
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.klienter.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.models.EndringsmeldingUtAdresse
import no.nav.eessi.pensjon.models.PdlEndringOpplysning
import no.nav.eessi.pensjon.models.Personopplysninger
import no.nav.eessi.pensjon.models.SedHendelse
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import no.nav.eessi.pensjon.pdl.filtrering.PdlFiltrering
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endringstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Kontaktadresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.KontaktadresseType
import no.nav.eessi.pensjon.personoppslag.pdl.model.Opplysningstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskAdresse
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

internal class AdresseoppdateringTest {

    var pdlService: PersonidentifiseringService = mockk()
    var euxService: EuxService = mockk(relaxed = true)
    var kodeverkClient: KodeverkClient = mockk(relaxed = true)

    var pdlFiltrering: PdlFiltrering = PdlFiltrering(kodeverkClient)

    private val personMottakKlient: PersonMottakKlient = mockk()

    @Test
    fun `Gitt SED uten utenlandsadresse, ingen oppdatering`() {
        val mockSedHendelse: SedHendelse = mockk(relaxed = true)
        val adresseoppdatering = Adresseoppdatering(mockk(), mockk(), mockk(), mockk())
        val result = adresseoppdatering.oppdaterUtenlandskKontaktadresse(mockSedHendelse)
        assertFalse(result)
    }

    @Test
    fun `Gitt SED med gyldig utlandsadresse, og denne finnes i PDL, saa skal det sendes oppdatering`() {
        val mockedSed: SED = mockk(relaxed = true)
        val adresseFraSED = Adresse(
            gate = "EddyRoad",
            bygning = "EddyHouse",
            by = "EddyCity",
            postnummer = "111",
            region = "Stockholm",
            land ="SWE",
            kontaktpersonadresse = null,
        )
        every { mockedSed.nav?.bruker?.adresse } returns adresseFraSED
        every { euxService.alleGyldigeSEDForBuc(eq("123456")) } returns listOf(Pair(mockk(), mockedSed))

        val identifisertPerson: IdentifisertPerson = identifisertPerson()
        every { pdlService.hentIdentifisertePersoner(any(), any()) } returns listOf(identifisertPerson)

        every { kodeverkClient.finnLandkode("SE") } returns "SWE"

        every { personMottakKlient.opprettPersonopplysning(any()) } returns true

        val adresseoppdatering = Adresseoppdatering(pdlService, euxService, personMottakKlient, pdlFiltrering)

        val sedHendelse = SedHendelse.fromJson(
            """{
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
        )
        val result = adresseoppdatering.oppdaterUtenlandskKontaktadresse(sedHendelse)

        assertTrue(result)

        verify(atLeast = 1) { pdlService.hentIdentifisertePersoner(any(), any()) }
        verify(atLeast = 1) {  personMottakKlient.opprettPersonopplysning(eq(PdlEndringOpplysning(listOf(
            Personopplysninger(
                endringstype = Endringstype.KORRIGER,
                ident = "11067122781",
                opplysningstype = Opplysningstype.KONTAKTADRESSE,
                endringsmelding = EndringsmeldingUtAdresse(
                    type = Opplysningstype.KONTAKTADRESSE.name,
                    kilde = "EESSI",  //TODO Finne ut om det er noe mer som skal fylles ut her
                    gyldigFraOgMed = LocalDate.now(),
                    gyldigTilOgMed = LocalDate.now().plusYears(1),
                    coAdressenavn = "c/o Anund",
                    adresse = UtenlandskAdresse(
                        adressenavnNummer = "EddyRoad",
                        bySted = "EddyCity",
                        bygningEtasjeLeilighet = "EddyHouse",
                        landkode = "SE",
                        postboksNummerNavn = null,
                        postkode = "111",
                        regionDistriktOmraade = "Stockholm"
                    )
                )
            )
        ))))
        }
    }

    private fun identifisertPerson(): IdentifisertPerson {
        return IdentifisertPerson(
            fnr = Fodselsnummer.fra("11067122781"),
            aktoerId = "",
            landkode = null,
            geografiskTilknytning = null,
            harAdressebeskyttelse = false,
            personRelasjon = mockk(),
            kontaktAdresse = Kontaktadresse(
                coAdressenavn = "c/o Anund",
                folkeregistermetadata = null,
                gyldigFraOgMed = LocalDateTime.now().minusDays(5),
                gyldigTilOgMed = LocalDateTime.now().plusDays(5),
                metadata = mockk(),
                type = KontaktadresseType.Utland,
                utenlandskAdresse = UtenlandskAdresse(
                    adressenavnNummer = "EddyRoad",
                    bySted = "EddyCity",
                    bygningEtasjeLeilighet = "EddyHouse",
                    landkode = "SE",
                    postkode = "111",
                    regionDistriktOmraade = "Stockholm"
                )
            )
        )
    }
}
