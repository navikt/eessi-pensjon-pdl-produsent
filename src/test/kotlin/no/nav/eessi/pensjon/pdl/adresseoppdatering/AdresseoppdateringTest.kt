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
import no.nav.eessi.pensjon.models.EndringsmeldingKontaktAdresse
import no.nav.eessi.pensjon.models.EndringsmeldingUtenlandskAdresse
import no.nav.eessi.pensjon.models.PdlEndringOpplysning
import no.nav.eessi.pensjon.models.Personopplysninger
import no.nav.eessi.pensjon.models.SedHendelse
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import no.nav.eessi.pensjon.pdl.filtrering.PdlFiltrering
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.AdressebeskyttelseGradering
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endringstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.Kontaktadresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.KontaktadresseType
import no.nav.eessi.pensjon.personoppslag.pdl.model.Metadata
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.Opplysningstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Person
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskAdresse
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

internal class AdresseoppdateringTest {

    var personService: PersonService = mockk()
    var euxService: EuxService = mockk(relaxed = true)
    var kodeverkClient: KodeverkClient = mockk(relaxed = true)

    var pdlFiltrering: PdlFiltrering = PdlFiltrering(kodeverkClient)

    private val personMottakKlient: PersonMottakKlient = mockk()

    @BeforeEach
    fun setup() {
        every { kodeverkClient.finnLandkode("SE") } returns "SWE"
    }

    @Test
    fun `Gitt SED uten utenlandsadresse, ingen oppdatering`() {
        val mockSedHendelse: SedHendelse = mockk(relaxed = true)
        val adresseoppdatering = Adresseoppdatering(mockk(), mockk(), mockk(), mockk())
        val result = adresseoppdatering.oppdaterUtenlandskKontaktadresse(mockSedHendelse)
        assertFalse(result)
    }

    @Test
    fun `Gitt SED med adresse med ulik landkode fra avsender, ingen oppdatering`() {
        every { euxService.hentSed(eq("123456"), eq("1234")) } returns
                sed("11067122781",
                    Adresse(
                        gate = "EddyRoad",
                        bygning = "EddyHouse",
                        by = "EddyCity",
                        postnummer = "111",
                        region = "Stockholm",
                        land = "SWE",
                        kontaktpersonadresse = null,
                    )
                )
        every { personService.hentPerson(NorskIdent("11067122781")) } returns
                personFraPDL(
                    "11067122781",
                    UtenlandskAdresse(
                        adressenavnNummer = "EddyRoad",
                        bySted = "EddyCity",
                        bygningEtasjeLeilighet = "EddyHouse",
                        landkode = "SE",
                        postkode = "111",
                        regionDistriktOmraade = "Stockholm"
                    ),
                    "OpplysningsId",
                    LocalDateTime.now().minusDays(5),
                    LocalDateTime.now().plusDays(5)
                )

        every { personMottakKlient.opprettPersonopplysning(any()) } returns true
        val adresseoppdatering = Adresseoppdatering(personService, euxService, personMottakKlient, pdlFiltrering)

        val result = adresseoppdatering.oppdaterUtenlandskKontaktadresse(sedHendelse(
            sektor = "P",
            bucType = "P_BUC_02",
            rinaSakId = "123456",
            avsenderNavn = "Republika Hrvatska",
            avsenderLand = "HR",
            rinaDokumentId = "1234",
            sedType = "P2100"
        ))
        assertFalse(result)
    }


    @Test
    fun `Gitt SED med gyldig utlandsadresse, og denne finnes i PDL, saa skal det sendes oppdatering med nye datoer`() {
        every { euxService.hentSed(eq("123456"), eq("1234")) } returns
                sed("11067122781",
                    Adresse(
                        gate = "EddyRoad",
                        bygning = "EddyHouse",
                        by = "EddyCity",
                        postnummer = "111",
                        region = "Stockholm",
                        land = "SWE",
                        kontaktpersonadresse = null,
                    )
                )
        every { personService.hentPerson(NorskIdent("11067122781")) } returns
                personFraPDL(
                    id = "11067122781",
                    utenlandskAdresse = UtenlandskAdresse(
                        adressenavnNummer = "EddyRoad",
                        bygningEtasjeLeilighet = "EddyHouse",
                        bySted = "EddyCity",
                        postkode = "111",
                        regionDistriktOmraade = "Stockholm",
                        landkode = "SE"
                    ),
                    opplysningsId = "OpplysningsId",
                    gyldigFraOgMed = LocalDateTime.now().minusDays(5),
                    gyldigTilOgMed = LocalDateTime.now().plusDays(5)
                )

        every { personMottakKlient.opprettPersonopplysning(any()) } returns true
        val adresseoppdatering = Adresseoppdatering(personService, euxService, personMottakKlient, pdlFiltrering)

        val result = adresseoppdatering.oppdaterUtenlandskKontaktadresse(sedHendelse(
            sektor = "P",
            bucType = "P_BUC_02",
            rinaSakId = "123456",
            avsenderNavn = "Svensk institusjon",
            avsenderLand = "SE",
            rinaDokumentId = "1234",
            sedType = "P2100"
        ))

        assertTrue(result)

        verify(atLeast = 1) { personMottakKlient.opprettPersonopplysning(eq(pdlEndringOpplysning(
            id = "11067122781",
            pdlAdresse = EndringsmeldingUtenlandskAdresse(
                adressenavnNummer = "EddyRoad",
                bygningEtasjeLeilighet = "EddyHouse",
                bySted = "EddyCity",
                postkode = "111",
                regionDistriktOmraade = "Stockholm",
                landkode = "SE",
                postboksNummerNavn = null // Dersom vi kan identifisere en postboksadresse så burde vi fylle det inn her
            ),
            opplysningsId = "OpplysningsId",
            kilde = "Svensk institusjon (SE)",
            gyldigFraOgMed = LocalDate.now(),
            gyldigTilOgMed = LocalDate.now().plusYears(1)
        ))) }
    }


    @Test
    fun `Gitt en Sed med lik adresse i pdl som har dagens dato som gyldig fom dato saa sendes ingen oppdatering`() {
        //TODO
    }

    private fun pdlEndringOpplysning(
        id: String,
        pdlAdresse: EndringsmeldingUtenlandskAdresse,
        opplysningsId: String,
        kilde: String,
        gyldigFraOgMed: LocalDate,
        gyldigTilOgMed: LocalDate
    ) = PdlEndringOpplysning(
        listOf(
            Personopplysninger(
                endringstype = Endringstype.KORRIGER,
                ident = id,
                opplysningstype = Opplysningstype.KONTAKTADRESSE,
                opplysningsId = opplysningsId,
                endringsmelding = EndringsmeldingKontaktAdresse(
                    type = Opplysningstype.KONTAKTADRESSE.name,
                    kilde = kilde,
                    gyldigFraOgMed = gyldigFraOgMed, // TODO er det rett å oppdatere denne datoen? Og hva om tilogmed-datoen er utløpt?
                    gyldigTilOgMed = gyldigTilOgMed,
                    coAdressenavn = "c/o Anund",
                    adresse = pdlAdresse
                )
            )
        )
    )

    private fun sedHendelse(
        sektor: String,
        bucType: String,
        rinaSakId: String,
        avsenderNavn: String?,
        avsenderLand: String?,
        rinaDokumentId: String,
        sedType: String
    ) = SedHendelse.fromJson(
        """{
                    "id" : 0,
                    
                    "sektorKode" : "$sektor",
                    "bucType" : "$bucType",
                    "rinaSakId" : "$rinaSakId",
                    "avsenderNavn" : ${if (avsenderNavn == null) "null" else "\"$avsenderNavn\""},
                    "avsenderLand" : ${if (avsenderLand == null) "null" else "\"$avsenderLand\""},
                    "mottakerNavn" : null,
                    "mottakerLand" : null,
                    "rinaDokumentId" : "$rinaDokumentId",
                    "rinaDokumentVersjon" : "1",
                    "sedType" : "$sedType"
                }""".trimMargin()
    )

    private fun sed(id: String, brukersAdresse: Adresse) = SED(
        type = SedType.P2100,
        sedGVer = null,
        sedVer = null,
        nav = Nav(
            eessisak = null,
            bruker = Bruker(
                mor = null,
                far = null,
                person = no.nav.eessi.pensjon.eux.model.sed.Person(
                    pin = listOf(
                        PinItem(
                            identifikator = id,
                            land = "NO"
                        )
                    ),
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
                adresse = brukersAdresse,
                arbeidsforhold = null,
                bank = null
            )
        ),
        pensjon = Pensjon()
    )

    private fun personFraPDL(
        id: String,
        utenlandskAdresse: UtenlandskAdresse,
        opplysningsId: String,
        gyldigFraOgMed: LocalDateTime,
        gyldigTilOgMed: LocalDateTime
    ) = Person(
        identer = listOf(IdentInformasjon(id, IdentGruppe.FOLKEREGISTERIDENT)),
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
            gyldigFraOgMed = gyldigFraOgMed,
            gyldigTilOgMed = gyldigTilOgMed,
            metadata = Metadata(
                endringer = emptyList(),
                historisk = false,
                master = "",
                opplysningsId = opplysningsId
            ),
            type = KontaktadresseType.Utland,
            utenlandskAdresse = utenlandskAdresse
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
