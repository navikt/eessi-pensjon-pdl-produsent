package no.nav.eessi.pensjon.pdl.adresseoppdatering

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.sed.Adresse
import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.Nav
import no.nav.eessi.pensjon.eux.model.sed.Pensjon
import no.nav.eessi.pensjon.eux.model.sed.PinItem
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.klienter.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.models.EndringsmeldingUtAdresse
import no.nav.eessi.pensjon.models.PdlEndringOpplysning
import no.nav.eessi.pensjon.models.Personopplysninger
import no.nav.eessi.pensjon.models.SedHendelse
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import no.nav.eessi.pensjon.pdl.filtrering.PdlFiltrering
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.AdressebeskyttelseGradering
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endringstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.Kontaktadresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.KontaktadresseType
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.Opplysningstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Person
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskAdresse
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

internal class AdresseoppdateringTest {

    var personService: PersonService = mockk()
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
        val sed = SED(
            type = SedType.P2100,
            sedGVer = null,
            sedVer = null,
            nav = Nav(
                eessisak = null,
                bruker = Bruker(
                    mor = null,
                    far = null,
                    person = no.nav.eessi.pensjon.eux.model.sed.Person(
                        pin = listOf(PinItem(
                            identifikator = "11067122781",
                            land = "NO"
                        )),
                        pinland = null,
                        statsborgerskap = null,
                        etternavn = null,
                        etternavnvedfoedsel = null,
                        fornavn = null,
                        fornavnvedfoedsel = null,
                        tidligerefornavn = null,
                        tidligereetternavn = null,
                        kjoenn = null,
                        foedested = null,
                        foedselsdato = null,
                        sivilstand = null,
                        relasjontilavdod = null,
                        rolle = null
                    ),
                    adresse = Adresse(
                        gate = "EddyRoad",
                        bygning = "EddyHouse",
                        by = "EddyCity",
                        postnummer = "111",
                        region = "Stockholm",
                        land ="SWE",
                        kontaktpersonadresse = null,
                    ),
                    arbeidsforhold = null,
                    bank = null
                )
            ),
            pensjon = Pensjon(

            )

        )

        every { euxService.hentSed(eq("123456"), eq("1234")) } returns sed

        every { personService.hentPerson(NorskIdent("11067122781")) } returns personFraPDL()

        every { kodeverkClient.finnLandkode("SE") } returns "SWE"

        every { personMottakKlient.opprettPersonopplysning(any()) } returns true

        val adresseoppdatering = Adresseoppdatering(personService, euxService, personMottakKlient, pdlFiltrering)

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

    private fun personFraPDL() = Person(
        identer = listOf(IdentInformasjon("11067122781", IdentGruppe.FOLKEREGISTERIDENT)),
        navn = null,
        adressebeskyttelse = listOf(AdressebeskyttelseGradering.UGRADERT),
        bostedsadresse = null,
        oppholdsadresse = null,
        statsborgerskap = listOf(),
        foedsel = null,
        geografiskTilknytning = null,
        kjoenn = null,
        doedsfall = null,
        forelderBarnRelasjon = listOf(),
        sivilstand = listOf(),
        kontaktadresse = Kontaktadresse(
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
        ),
        kontaktinformasjonForDoedsbo = null,
        utenlandskIdentifikasjonsnummer = listOf()
    )

    @Test
    fun `Gitt person med adressebeskyttelse så XXX`() {
        // given

        // when

        // then

    }
}
