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

private const val RINA_SAK_ID = "123456"
private const val DOKUMENT_ID = "1234"
private const val FNR = "11067122781"
private const val EDDY_ADRESSE_LANDKODE = "SE"

private val EDDY_ADRESSE_I_SED = Adresse(
    gate = "EddyRoad",
    bygning = "EddyHouse",
    by = "EddyCity",
    postnummer = "111",
    region = "Stockholm",
    land = EDDY_ADRESSE_LANDKODE,
    kontaktpersonadresse = null,
)

private val EDDY_ADRESSE_FRA_PDL = UtenlandskAdresse(
    adressenavnNummer = "EddyRoad",
    bygningEtasjeLeilighet = "EddyHouse",
    bySted = "EddyCity",
    postkode = "111",
    regionDistriktOmraade = "Stockholm",
    landkode = "SWE"
)

private val EDDY_ADRESSE_I_ENDRINGSMELDING = EndringsmeldingUtenlandskAdresse(
    adressenavnNummer = "EddyRoad",
    bygningEtasjeLeilighet = "EddyHouse",
    bySted = "EddyCity",
    postkode = "111",
    regionDistriktOmraade = "Stockholm",
    landkode = "SWE",
    postboksNummerNavn = null // Dersom vi kan identifisere en postboksadresse så burde vi fylle det inn her
)


internal class AdresseoppdateringTest {

    var personService: PersonService = mockk()
    var euxService: EuxService = mockk(relaxed = true)
    var kodeverkClient: KodeverkClient = mockk(relaxed = true)

    var pdlFiltrering: PdlFiltrering = PdlFiltrering(kodeverkClient)

    private val personMottakKlient: PersonMottakKlient = mockk()

    @BeforeEach
    fun setup() {
        every { kodeverkClient.finnLandkode("SWE") } returns EDDY_ADRESSE_LANDKODE
        every { kodeverkClient.finnLandkode(EDDY_ADRESSE_LANDKODE) } returns "SWE"
    }

    @Test
    fun `Gitt SED uten utenlandsadresse, ingen oppdatering`() {
        val mockSedHendelse: SedHendelse = mockk(relaxed = true)
        val adresseoppdatering = Adresseoppdatering(mockk(), mockk(), mockk(), mockk())
        val result = adresseoppdatering.oppdaterUtenlandskKontaktadresse(mockSedHendelse)
        assertFalse(result)
    }

    @Test
    fun `Gitt SED med gyldig utlandsadresse, og denne finnes i PDL, saa skal det sendes oppdatering med nye datoer`() {
        every { euxService.hentSed(eq(RINA_SAK_ID), eq(DOKUMENT_ID)) } returns
                sed(
                    id = FNR,
                    brukersAdresse = EDDY_ADRESSE_I_SED
                )
        every { personService.hentPerson(NorskIdent(FNR)) } returns
                personFraPDL(
                    id = FNR,
                    utenlandskAdresse = EDDY_ADRESSE_FRA_PDL,
                    opplysningsId = "OpplysningsId",
                    gyldigFraOgMed = LocalDateTime.now().minusDays(5),
                    gyldigTilOgMed = LocalDateTime.now().plusDays(5)
                )

        every { personMottakKlient.opprettPersonopplysning(any()) } returns true
        val adresseoppdatering = Adresseoppdatering(personService, euxService, personMottakKlient, pdlFiltrering)

        val result = adresseoppdatering.oppdaterUtenlandskKontaktadresse(
            sedHendelse(
                sektor = "P",
                bucType = "P_BUC_02",
                rinaSakId = RINA_SAK_ID,
                avsenderNavn = "Utenlandsk Institusjon",
                avsenderLand = EDDY_ADRESSE_LANDKODE,
                rinaDokumentId = DOKUMENT_ID,
                sedType = "P2100"
            ))

        assertTrue(result)

        verify(atLeast = 1) { personMottakKlient.opprettPersonopplysning(eq(
            pdlEndringOpplysning(
                id = FNR,
                pdlAdresse = EDDY_ADRESSE_I_ENDRINGSMELDING,
                opplysningsId = "OpplysningsId",
                kilde = "Utenlandsk Institusjon ($EDDY_ADRESSE_LANDKODE)",
                gyldigFraOgMed = LocalDate.now(),
                gyldigTilOgMed = LocalDate.now().plusYears(1)
            ))) }
    }

    @Test
    fun `Gitt SED med adresse med ulik landkode fra avsender, ingen oppdatering`() {
        every { euxService.hentSed(eq(RINA_SAK_ID), eq(DOKUMENT_ID)) } returns
                sed(brukersAdresse = adresse(EDDY_ADRESSE_LANDKODE))

        val adresseoppdatering = Adresseoppdatering(mockk(), euxService, mockk(), mockk())

        val result = adresseoppdatering.oppdaterUtenlandskKontaktadresse(sedHendelse(
            rinaSakId = RINA_SAK_ID,
            avsenderNavn = "Dansk institusjon",
            avsenderLand = "DK",
            rinaDokumentId = DOKUMENT_ID
        ))
        assertFalse(result)
    }

    @Test
    fun `Gitt en Sed med lik adresse i pdl som har dagens dato som gyldig fom dato saa sendes ingen oppdatering`() {
        every { euxService.hentSed(eq(RINA_SAK_ID), eq(DOKUMENT_ID)) } returns
                sed(
                    FNR,
                    EDDY_ADRESSE_I_SED
                )
        every { personService.hentPerson(NorskIdent(FNR)) } returns
                personFraPDL(
                    id = FNR,
                    utenlandskAdresse = EDDY_ADRESSE_FRA_PDL,
                    gyldigFraOgMed = LocalDateTime.now()
                )

        val adresseoppdatering = Adresseoppdatering(personService, euxService, personMottakKlient, pdlFiltrering)

        val result = adresseoppdatering.oppdaterUtenlandskKontaktadresse(
            sedHendelse(
                rinaSakId = RINA_SAK_ID,
                avsenderNavn = "Svensk institusjon",
                avsenderLand = EDDY_ADRESSE_LANDKODE,
                rinaDokumentId = DOKUMENT_ID,
            ))

        assertFalse(result)

    }

    @Test
    fun `Gitt en sed med postboksadresse i gatefeltet som er lik postboksadresse i pdl saa oppdateres datoer`() {

    }

    @Test
    fun `Gitt person med adressebeskyttelse så XXX`() {

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
        sektor: String = "P",
        bucType: String = "P_BUC_02",
        rinaSakId: String,
        avsenderNavn: String? = null,
        avsenderLand: String? = null,
        rinaDokumentId: String,
        sedType: String = "P2100"
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

    private fun sed(id: String = "65356", brukersAdresse: Adresse) = SED(
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

    private fun adresse(land: String) = Adresse(
        gate = null,
        bygning = null,
        by = null,
        postnummer = null,
        region = null,
        land = land,
        kontaktpersonadresse = null,
    )

    private fun personFraPDL(
        id: String,
        utenlandskAdresse: UtenlandskAdresse,
        opplysningsId: String = "DummyOpplysningsId",
        gyldigFraOgMed: LocalDateTime = LocalDateTime.now().minusDays(10),
        gyldigTilOgMed: LocalDateTime = LocalDateTime.now().plusDays(10)
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

}
