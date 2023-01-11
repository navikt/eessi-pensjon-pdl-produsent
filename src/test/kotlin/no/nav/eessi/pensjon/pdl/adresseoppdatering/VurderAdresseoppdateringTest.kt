package no.nav.eessi.pensjon.pdl.adresseoppdatering

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.sed.Adresse
import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.Nav
import no.nav.eessi.pensjon.eux.model.sed.Pensjon
import no.nav.eessi.pensjon.eux.model.sed.PinItem
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.models.EndringsmeldingKontaktAdresse
import no.nav.eessi.pensjon.models.EndringsmeldingUtenlandskAdresse
import no.nav.eessi.pensjon.models.PdlEndringOpplysning
import no.nav.eessi.pensjon.models.Personopplysninger
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import no.nav.eessi.pensjon.pdl.adresseoppdatering.VurderAdresseoppdatering.IngenOppdatering
import no.nav.eessi.pensjon.pdl.adresseoppdatering.VurderAdresseoppdatering.Oppdatering
import no.nav.eessi.pensjon.shared.person.FodselsnummerGenerator
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.PersonoppslagException
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
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalDateTime

private const val SOME_RINA_SAK_ID = "123456"
private const val SOME_DOKUMENT_ID = "1234"
private const val SOME_UTENLANDSK_INSTITUSJON = "Utenlandsk Institusjon"

internal class VurderAdresseoppdateringTest {

    var personService: PersonService = mockk()
    var euxService: EuxService = mockk(relaxed = true)
    var kodeverkClient: KodeverkClient = mockk(relaxed = true)
    var sedTilPDLAdresse = SedTilPDLAdresse(kodeverkClient)

    val SOME_FNR = FodselsnummerGenerator.generateFnrForTest(77)
    val FNR = FodselsnummerGenerator.generateFnrForTest(65)

    private val personMottakKlient: PersonMottakKlient = mockk()

    @BeforeEach
    fun setup() {
        every { kodeverkClient.finnLandkode("SWE") } returns "SE"
        every { kodeverkClient.finnLandkode("SE") } returns "SWE"
        every { kodeverkClient.finnLandkode("DE") } returns "DEU"
        every { kodeverkClient.finnLandkode("DEU") } returns "DE"
    }

    @Test
    fun `Gitt SED med gyldig utlandsadresse, og denne finnes i PDL, saa skal det sendes oppdatering med nye datoer`() {
        every { euxService.hentSed(eq(SOME_RINA_SAK_ID), eq(SOME_DOKUMENT_ID)) } returns
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
        val adresseoppdatering = VurderAdresseoppdatering(personService, euxService, sedTilPDLAdresse)

        val result = adresseoppdatering.vurderUtenlandskKontaktadresse(
            sedHendelse(
                sektor = "P",
                bucType = "P_BUC_02",
                rinaSakId = SOME_RINA_SAK_ID,
                avsenderNavn = SOME_UTENLANDSK_INSTITUSJON,
                avsenderLand = EDDY_ADRESSE_LANDKODE,
                rinaDokumentId = SOME_DOKUMENT_ID,
                sedType = "P2100"
            ))

        assertEquals(
            Oppdatering(
                "Adressen fra $EDDY_ADRESSE_LANDKODE finnes allerede i PDL, oppdaterer gyldig til og fra dato",
                pdlEndringOpplysning(
                    id = FNR,
                    pdlAdresse = EDDY_ADRESSE_I_ENDRINGSMELDING,
                    opplysningsId = "OpplysningsId",
                    kilde = "Utenlandsk Institusjon ($EDDY_ADRESSE_LANDKODE)",
                    gyldigFraOgMed = LocalDate.now(),
                    gyldigTilOgMed = LocalDate.now().plusYears(5)
                ), "Adressen finnes allerede i PDL, oppdaterer gyldig til og fra dato"
            ), result
        )

    }

    private val EDDY_ADRESSE_LANDKODE = "SE"

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

    @Test
    fun `Gitt en sed med en tysk bostedsadresse fra en institusjon i Tyskland og den ikke finnes i PDL saa skal PDL oppdateres med ny bostedsadresse`() {
        every { euxService.hentSed(eq(SOME_RINA_SAK_ID), eq(SOME_DOKUMENT_ID)) } returns
                sed(brukersAdresse = TYSK_ADRESSE_I_SED)

        every { personService.hentPerson(NorskIdent(SOME_FNR)) } returns
                personFraPDL()

        every { personMottakKlient.opprettPersonopplysning(any()) } returns true

        val adresseoppdatering = VurderAdresseoppdatering(personService, euxService, sedTilPDLAdresse)

        val result = adresseoppdatering.vurderUtenlandskKontaktadresse(sedHendelse(avsenderNavn = TYSK_INSTITUSJON, avsenderLand = TYSK_ADRESSE_LANDKODE))

        assertEquals(Oppdatering("Adressen i SED fra $TYSK_ADRESSE_LANDKODE finnes ikke i PDL, sender OPPRETT endringsmelding",
            pdlAdresseEndringsOpplysning(
                pdlAdresse = TYSK_ADRESSE_I_SED_GJORT_OM_TIL_PDL_ADRESSE,
                kilde = "$TYSK_INSTITUSJON ($TYSK_ADRESSE_LANDKODE)",
                gyldigFraOgMed = LocalDate.now(),
                gyldigTilOgMed = LocalDate.now().plusYears(5),
                endringsType = Endringstype.OPPRETT
            ), metricTagValueOverride = "Adressen i SED finnes ikke i PDL, sender OPPRETT endringsmelding")
        , result)
    }

    private val TYSK_ADRESSE_LANDKODE = "DE"
    private val TYSK_INSTITUSJON = "Tysk institusjon"

    private val TYSK_ADRESSE_I_SED = Adresse(
        gate = "Tysk gateadresse",
        bygning = "Tysk bygning",
        by = "München",
        postnummer = "2222",
        region = "Bavaria",
        land = TYSK_ADRESSE_LANDKODE,
        kontaktpersonadresse = null,
    )

    private val TYSK_ADRESSE_I_SED_GJORT_OM_TIL_PDL_ADRESSE = EndringsmeldingUtenlandskAdresse(
        adressenavnNummer = "Tysk gateadresse",
        bygningEtasjeLeilighet = "Tysk bygning",
        bySted = "München",
        postkode = "2222",
        regionDistriktOmraade = "Bavaria",
        landkode = "DEU",
        postboksNummerNavn = null // Dersom vi kan identifisere en postboksadresse så burde vi fylle det inn her
    )
    @Test
    fun `Gitt en sed med en tysk bostedsadresse fra en institusjon i Tyskland og den ikke finnes i PDL saa skal PDL oppdateres med ny kontaktadresse`() {
        every { euxService.hentSed(eq(SOME_RINA_SAK_ID), eq(SOME_DOKUMENT_ID)) } returns
                sed(brukersAdresse = TYSK_ADRESSE_I_SED)

        every { personService.hentPerson(NorskIdent(SOME_FNR)) } returns
                personFraPDL(
                    id = SOME_FNR,
                    utenlandskAdresse = EDDY_ADRESSE_FRA_PDL,
                    gyldigFraOgMed = LocalDateTime.now().minusDays(5),
                    gyldigTilOgMed = LocalDateTime.now().plusDays(5),
                )

        every { personMottakKlient.opprettPersonopplysning(any()) } returns true

        val adresseoppdatering = VurderAdresseoppdatering(personService, euxService, sedTilPDLAdresse)

        val result = adresseoppdatering.vurderUtenlandskKontaktadresse(sedHendelse(avsenderNavn = TYSK_INSTITUSJON, avsenderLand = TYSK_ADRESSE_LANDKODE))

        assertEquals(Oppdatering("Adressen i SED fra $TYSK_ADRESSE_LANDKODE finnes ikke i PDL, sender OPPRETT endringsmelding",
            pdlAdresseEndringsOpplysning(
                pdlAdresse = TYSK_ADRESSE_I_SED_GJORT_OM_TIL_PDL_ADRESSE,
                kilde = "$TYSK_INSTITUSJON ($TYSK_ADRESSE_LANDKODE)",
                endringsType = Endringstype.OPPRETT
            ), "Adressen i SED finnes ikke i PDL, sender OPPRETT endringsmelding"
        ), result)
    }

    @Test
    fun `Gitt SED med gyldig utlandsadresse, og selv om denne finnes i PDL som en adresse fra FREG, så oppretter vi en ny kontaktadresse likevel`() {
        every { euxService.hentSed(any(), any()) } returns sed(brukersAdresse = EDDY_ADRESSE_I_SED)
        every { personService.hentPerson(NorskIdent(SOME_FNR)) } returns
                personFraPDL(
                    utenlandskAdresse = EDDY_ADRESSE_FRA_PDL,
                    metadataMaster = "Freg"
                )
        every { personMottakKlient.opprettPersonopplysning(any()) } returns true
        val adresseoppdatering = VurderAdresseoppdatering(personService, euxService, sedTilPDLAdresse)

        val result = adresseoppdatering.vurderUtenlandskKontaktadresse(sedHendelse(avsenderLand = EDDY_ADRESSE_LANDKODE))

        val forventetPdlEndringsOpplysninger = pdlAdresseEndringsOpplysning(
            endringsType = Endringstype.OPPRETT,
            pdlAdresse = EDDY_ADRESSE_I_ENDRINGSMELDING,
            kilde = "Utenlandsk Institusjon ($EDDY_ADRESSE_LANDKODE)",
        )

        assertEquals(
            Oppdatering(
                "Adressen i SED fra SE finnes ikke i PDL, sender OPPRETT endringsmelding",
                forventetPdlEndringsOpplysninger, "Adressen i SED finnes ikke i PDL, sender OPPRETT endringsmelding"
            ), result
        )
    }

    @Test
    fun `Gitt en sed med ustandard norsk ident så skal det likevel oppdateres`() {
        val norskFnr = FodselsnummerGenerator.generateFnrForTest(69)
        val norskFnrMedMellomrom = norskFnr.substring(0..5) + " " + norskFnr.substring(6)

        every { euxService.hentSed(eq(SOME_RINA_SAK_ID), eq(SOME_DOKUMENT_ID)) } returns
                sed(brukersAdresse = TYSK_ADRESSE_I_SED, id = norskFnrMedMellomrom)
        every { personService.hentPerson(NorskIdent(norskFnr)) } returns personFraPDL(id = norskFnr)
        every { personMottakKlient.opprettPersonopplysning(any()) } returns true

        val adresseoppdatering = VurderAdresseoppdatering(personService, euxService, sedTilPDLAdresse)

        val result = adresseoppdatering.vurderUtenlandskKontaktadresse(sedHendelse(avsenderNavn = TYSK_INSTITUSJON, avsenderLand = TYSK_ADRESSE_LANDKODE))

        assertEquals(Oppdatering("Adressen i SED fra $TYSK_ADRESSE_LANDKODE finnes ikke i PDL, sender OPPRETT endringsmelding",
            pdlAdresseEndringsOpplysning(
                id = norskFnr,
                pdlAdresse = TYSK_ADRESSE_I_SED_GJORT_OM_TIL_PDL_ADRESSE,
                kilde = "$TYSK_INSTITUSJON ($TYSK_ADRESSE_LANDKODE)",
                endringsType = Endringstype.OPPRETT
            ), metricTagValueOverride = "Adressen i SED finnes ikke i PDL, sender OPPRETT endringsmelding"
        ), result)
    }


    @Test
    fun `Gitt en sed med norsk ident som ikke validerer så skal vi ikke oppdatere`() {
        val norskFnr = "okänd"

        every { euxService.hentSed(eq(SOME_RINA_SAK_ID), eq(SOME_DOKUMENT_ID)) } returns
                sed(brukersAdresse = TYSK_ADRESSE_I_SED, id = norskFnr)

        val adresseoppdatering = VurderAdresseoppdatering(personService, euxService, mockk())

        val result = adresseoppdatering.vurderUtenlandskKontaktadresse(sedHendelse(avsenderNavn = TYSK_INSTITUSJON, avsenderLand = TYSK_ADRESSE_LANDKODE))

        assertEquals(IngenOppdatering(
            "Brukers norske id fra SED validerer ikke: \"$norskFnr\" - Ikke et gyldig fødselsnummer: ",
            "Brukers norske id fra SED validerer ikke"
        ), result)
    }

    @Test
    fun `Gitt en sed med midlertidig norsk d-nummer, som PDL returnerer fnr på, oppdateres på fnr`() {
        val midlertidigNorskId = "51077403071"
        val norskFnr = FodselsnummerGenerator.generateFnrForTest(69)

        every { euxService.hentSed(eq(SOME_RINA_SAK_ID), eq(SOME_DOKUMENT_ID)) } returns
                sed(brukersAdresse = TYSK_ADRESSE_I_SED, id = midlertidigNorskId)
        every { personService.hentPerson(NorskIdent(midlertidigNorskId)) } returns personFraPDL(id = norskFnr)
        every { personMottakKlient.opprettPersonopplysning(any()) } returns true

        val adresseoppdatering = VurderAdresseoppdatering(personService, euxService, sedTilPDLAdresse)

        val result = adresseoppdatering.vurderUtenlandskKontaktadresse(
            sedHendelse(
                avsenderNavn = TYSK_INSTITUSJON,
                avsenderLand = TYSK_ADRESSE_LANDKODE
            )
        )

        val forventetAdresseEndringsOpplysning = pdlAdresseEndringsOpplysning(
            id = norskFnr,
            pdlAdresse = TYSK_ADRESSE_I_SED_GJORT_OM_TIL_PDL_ADRESSE,
            kilde = "$TYSK_INSTITUSJON ($TYSK_ADRESSE_LANDKODE)",
            endringsType = Endringstype.OPPRETT
        )
        assertEquals(forventetAdresseEndringsOpplysning, (result as Oppdatering).pdlEndringsOpplysninger)

        assertEquals(
            Oppdatering(
                "Adressen i SED fra $TYSK_ADRESSE_LANDKODE finnes ikke i PDL, sender OPPRETT endringsmelding",
                forventetAdresseEndringsOpplysning,
                metricTagValueOverride = "Adressen i SED finnes ikke i PDL, sender OPPRETT endringsmelding"
            ), result
        )
    }

    @Test
    fun `Gitt en sed med norsk ident som validerer men ikke finnes i PDL så skal vi ikke oppdatere`() {
        val norskFnr = "06085692087"
        every { euxService.hentSed(eq(SOME_RINA_SAK_ID), eq(SOME_DOKUMENT_ID)) } returns sed(brukersAdresse = TYSK_ADRESSE_I_SED, id = norskFnr)
        every { personService.hentPerson(NorskIdent(norskFnr)) } throws PersonoppslagException("Fant ikke person", "not_found")

        val adresseoppdatering = VurderAdresseoppdatering(personService, euxService, mockk())

        val result = adresseoppdatering.vurderUtenlandskKontaktadresse(sedHendelse(avsenderNavn = TYSK_INSTITUSJON, avsenderLand = TYSK_ADRESSE_LANDKODE))

        assertEquals(IngenOppdatering("Finner ikke bruker i PDL med angitt fnr i SED"), result)
    }

    @Test
    fun `Gitt en uventet feil fra PDL så skal vi kaste en exception`() {

        every { euxService.hentSed(eq(SOME_RINA_SAK_ID), eq(SOME_DOKUMENT_ID)) } returns sed(brukersAdresse = TYSK_ADRESSE_I_SED)
        every { personService.hentPerson(NorskIdent(SOME_FNR)) } throws PersonoppslagException("Uventet feil", "uventet kode")

        val adresseoppdatering = VurderAdresseoppdatering(personService, euxService, mockk())

        assertThrows<PersonoppslagException> {
            adresseoppdatering.vurderUtenlandskKontaktadresse(sedHendelse(avsenderNavn = TYSK_INSTITUSJON, avsenderLand = TYSK_ADRESSE_LANDKODE))
        }
    }

    @Test
    fun `Gitt SED med adresse med ulik landkode fra avsender, ingen oppdatering`() {
        every { euxService.hentSed(eq(SOME_RINA_SAK_ID), eq(SOME_DOKUMENT_ID)) } returns
                sed(brukersAdresse = adresse(EDDY_ADRESSE_LANDKODE))

        val adresseoppdatering = VurderAdresseoppdatering(mockk(), euxService, mockk())

        val result = adresseoppdatering.vurderUtenlandskKontaktadresse(sedHendelse(
            avsenderNavn = "Dansk institusjon",
            avsenderLand = "DK",
        ))

        assertEquals(IngenOppdatering(
            "Adressens landkode (${EDDY_ADRESSE_LANDKODE}) er ulik landkode på avsenderland (DK)",
            "Adressens landkode er ulik landkode på avsenderland"
        ), result)
    }

    @Test
    fun `Gitt SED uten utenlandsk adresse resulterer ikke i NoUpdate`() {
        every { euxService.hentSed(eq(SOME_RINA_SAK_ID), eq(SOME_DOKUMENT_ID)) } returns
                sed(brukersAdresse = adresse("NO"))

        val adresseoppdatering = VurderAdresseoppdatering(mockk(), euxService, mockk())

        val result = adresseoppdatering.vurderUtenlandskKontaktadresse(sedHendelse())
        assertEquals(IngenOppdatering("Bruker har ikke utenlandsk adresse i SED"), result)
    }

    @Test
    fun `Gitt en SED uten avsenderland saa resulterer det i en Exception`() {
        every { euxService.hentSed(eq(SOME_RINA_SAK_ID), eq(SOME_DOKUMENT_ID)) } returns
                sed(brukersAdresse = adresse(EDDY_ADRESSE_LANDKODE))

        val adresseoppdatering = VurderAdresseoppdatering(mockk(), euxService, mockk())

        val ex = assertThrows<IllegalArgumentException> {
            adresseoppdatering.vurderUtenlandskKontaktadresse(sedHendelse(avsenderLand = null))
        }
        assertTrue(ex.message!!.startsWith("Mangler avsenderNavn eller avsenderLand i sedHendelse - avslutter adresseoppdatering: SedHendelse"))
    }

    @Test
    fun `Gitt en SED uten norsk pin saa oppdaterer vi ikke`() {
        every { euxService.hentSed(eq(SOME_RINA_SAK_ID), eq(SOME_DOKUMENT_ID)) } returns
                sed(
                    brukersAdresse = EDDY_ADRESSE_I_SED,
                    pinItem = PinItem(
                        identifikator = "svensk identifikator",
                        land = "SE"
                    )
                )

        val adresseoppdatering = VurderAdresseoppdatering(personService, euxService, mockk())

        val result = adresseoppdatering.vurderUtenlandskKontaktadresse(sedHendelse(avsenderLand = "SE"))
        assertEquals(IngenOppdatering("Bruker har ikke norsk pin i SED"), result)
    }

    @Test
    fun `Gitt en Sed med lik adresse i pdl som har dagens dato som gyldig fom dato saa sendes ingen oppdatering`() {
        every { euxService.hentSed(eq(SOME_RINA_SAK_ID), eq(SOME_DOKUMENT_ID)) } returns
                sed(brukersAdresse = EDDY_ADRESSE_I_SED)

        every { personService.hentPerson(NorskIdent(SOME_FNR)) } returns
                personFraPDL(
                    utenlandskAdresse = EDDY_ADRESSE_FRA_PDL,
                    gyldigFraOgMed = LocalDateTime.now()
                )

        val adresseoppdatering = VurderAdresseoppdatering(personService, euxService, sedTilPDLAdresse)

        val result = adresseoppdatering.vurderUtenlandskKontaktadresse(
            sedHendelse(
                avsenderNavn = "Svensk institusjon",
                avsenderLand = EDDY_ADRESSE_LANDKODE,
            ))

        assertEquals(IngenOppdatering("Adresse finnes allerede i PDL med dagens dato som gyldig-fra-dato, dropper oppdatering"), result)

    }

    @Test
    fun `Gitt en sed med postboksadresse i gatefeltet som er lik postboksadresse i pdl saa oppdateres datoer`() {
        every { euxService.hentSed(eq(SOME_RINA_SAK_ID), eq(SOME_DOKUMENT_ID)) } returns
                sed(brukersAdresse = EDDY_ADRESSE_I_SED.copy(gate = "Postboks 543"))

        every { personService.hentPerson(NorskIdent(SOME_FNR)) } returns
                personFraPDL(
                    utenlandskAdresse = EDDY_ADRESSE_FRA_PDL.copy(postboksNummerNavn = "Postboks 543", adressenavnNummer = null)
                )

        every { personMottakKlient.opprettPersonopplysning(any()) } returns true
        val adresseoppdatering = VurderAdresseoppdatering(personService, euxService, sedTilPDLAdresse)

        val result = adresseoppdatering.vurderUtenlandskKontaktadresse(sedHendelse(avsenderLand = EDDY_ADRESSE_LANDKODE))

        assertEquals(Oppdatering("Adressen fra $EDDY_ADRESSE_LANDKODE finnes allerede i PDL, oppdaterer gyldig til og fra dato",
            pdlEndringOpplysning(
            pdlAdresse = EDDY_ADRESSE_I_ENDRINGSMELDING.copy(postboksNummerNavn = "Postboks 543", adressenavnNummer = null),
            kilde = "$SOME_UTENLANDSK_INSTITUSJON ($EDDY_ADRESSE_LANDKODE)",
            gyldigFraOgMed = LocalDate.now(),
            gyldigTilOgMed = LocalDate.now().plusYears(5)),
            "Adressen finnes allerede i PDL, oppdaterer gyldig til og fra dato"
        ), result)

    }

    @Test
    fun `Gitt person med adressebeskyttelse (i Norge) så gjør vi ingen oppdatering`() {
        every { euxService.hentSed(eq(SOME_RINA_SAK_ID), eq(SOME_DOKUMENT_ID)) } returns sed(brukersAdresse = EDDY_ADRESSE_I_SED)

        every { personService.hentPerson(NorskIdent(SOME_FNR)) } returns
                personFraPDL(
                    adressebeskyttelse = listOf(AdressebeskyttelseGradering.STRENGT_FORTROLIG)
                )

        val adresseoppdatering = VurderAdresseoppdatering(personService, euxService, mockk())

        val result = adresseoppdatering.vurderUtenlandskKontaktadresse(sedHendelse(avsenderLand = EDDY_ADRESSE_LANDKODE))

        assertEquals(IngenOppdatering("Ingen adresseoppdatering"), result)

    }

    @Test
    fun `Gitt at en person har adressebeskyttelse fortrolig utland saa skal adressen likevel registreres`() {
        every { euxService.hentSed(eq(SOME_RINA_SAK_ID), eq(SOME_DOKUMENT_ID)) } returns sed(brukersAdresse = EDDY_ADRESSE_I_SED)

        every { personService.hentPerson(NorskIdent(SOME_FNR)) } returns
                personFraPDL(
                    adressebeskyttelse = listOf(AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND)
                )

        val adresseoppdatering = VurderAdresseoppdatering(personService, euxService, sedTilPDLAdresse)

        val result = adresseoppdatering.vurderUtenlandskKontaktadresse(sedHendelse(avsenderLand = EDDY_ADRESSE_LANDKODE))

        assertTrue(result is Oppdatering)
    }

    @Test
    fun `Gitt en SED med postboksadresse i gatefeltet saa skal denne fylles ut i postboks feltet`() {
        every { euxService.hentSed(eq(SOME_RINA_SAK_ID), eq(SOME_DOKUMENT_ID)) } returns sed(brukersAdresse = EDDY_ADRESSE_I_SED.copy(gate = "postbox Dette er en gyldig postboksadresse"))
        every { personService.hentPerson(NorskIdent(SOME_FNR)) } returns personFraPDL()

        val adresseoppdatering = VurderAdresseoppdatering(personService, euxService, sedTilPDLAdresse)

        val result = adresseoppdatering.vurderUtenlandskKontaktadresse(sedHendelse(avsenderLand = EDDY_ADRESSE_LANDKODE))

        assertEquals(
            Oppdatering(
                "Adressen i SED fra $EDDY_ADRESSE_LANDKODE finnes ikke i PDL, sender OPPRETT endringsmelding",
                pdlAdresseEndringsOpplysning(
                    id = SOME_FNR,
                    pdlAdresse = EDDY_ADRESSE_I_ENDRINGSMELDING
                        .copy(
                            postboksNummerNavn = "postbox Dette er en gyldig postboksadresse",
                            adressenavnNummer = null
                        ),
                    kilde = "$SOME_UTENLANDSK_INSTITUSJON ($EDDY_ADRESSE_LANDKODE)",
                    gyldigFraOgMed = LocalDate.now(),
                    gyldigTilOgMed = LocalDate.now().plusYears(5),
                    endringsType = Endringstype.OPPRETT
                ), metricTagValueOverride = "Adressen i SED finnes ikke i PDL, sender OPPRETT endringsmelding"
            ), result)

    }

    @Test
    fun `Gitt en adresse som ikke validerer saa oppdaterer vi ikke PDL`() {
        every { euxService.hentSed(eq(SOME_RINA_SAK_ID), eq(SOME_DOKUMENT_ID)) } returns sed(
            id = SOME_FNR, brukersAdresse = EDDY_ADRESSE_I_SED.copy(gate = "Ukjent"), pinItem = PinItem(
                identifikator = SOME_FNR,
                land = "NO"
            )
        )

        every { personService.hentPerson(NorskIdent(SOME_FNR)) } returns personFraPDL(id = SOME_FNR)

        val adresseoppdatering = VurderAdresseoppdatering(personService, euxService, sedTilPDLAdresse)

        val result = adresseoppdatering.vurderUtenlandskKontaktadresse(sedHendelse(avsenderLand = EDDY_ADRESSE_LANDKODE))

        assertEquals(IngenOppdatering(
            "Adressen fra $EDDY_ADRESSE_LANDKODE validerer ikke etter reglene til PDL: Ikke gyldig adressenavnNummer: Ukjent",
            "Adressen validerer ikke etter reglene til PDL"
        ), result)

    }

    private fun pdlEndringOpplysning(
        id: String = SOME_FNR,
        pdlAdresse: EndringsmeldingUtenlandskAdresse,
        opplysningsId: String = "DummyOpplysningsId",
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

    private fun pdlAdresseEndringsOpplysning(
        id: String = SOME_FNR,
        pdlAdresse: EndringsmeldingUtenlandskAdresse,
        kilde: String,
        gyldigFraOgMed: LocalDate = LocalDate.now(),
        gyldigTilOgMed: LocalDate = LocalDate.now().plusYears(5),
        endringsType: Endringstype
    ) = PdlEndringOpplysning(
        listOf(
            Personopplysninger(
                endringstype = endringsType,
                ident = id,
                opplysningstype = Opplysningstype.KONTAKTADRESSE,
                endringsmelding = EndringsmeldingKontaktAdresse(
                    type = Opplysningstype.KONTAKTADRESSE.name,
                    kilde = kilde,
                    gyldigFraOgMed = gyldigFraOgMed,
                    gyldigTilOgMed = gyldigTilOgMed,
                    coAdressenavn = null,
                    adresse = pdlAdresse
                )
            )
        )
    )

    private fun sedHendelse(
        sektor: String = "P",
        bucType: String = "P_BUC_02",
        rinaSakId: String = SOME_RINA_SAK_ID,
        avsenderNavn: String? = SOME_UTENLANDSK_INSTITUSJON,
        avsenderLand: String? = "IT",
        rinaDokumentId: String = SOME_DOKUMENT_ID,
        sedType: String = "P2100"
    ) = mapJsonToAny<SedHendelse>(
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

    private fun sed(id: String = SOME_FNR, brukersAdresse: Adresse? = null, pinItem: PinItem? = null) = SED(
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
                        pinItem ?: PinItem(
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
        id: String = SOME_FNR,
        adressebeskyttelse: List<AdressebeskyttelseGradering> = listOf(),
        utenlandskAdresse: UtenlandskAdresse? = null,
        opplysningsId: String = "DummyOpplysningsId",
        gyldigFraOgMed: LocalDateTime = LocalDateTime.now().minusDays(10),
        gyldigTilOgMed: LocalDateTime = LocalDateTime.now().plusDays(10),
        metadataMaster: String = "PDL"
    ) = Person(
        identer = listOf(IdentInformasjon(id, IdentGruppe.FOLKEREGISTERIDENT)),
        navn = null,
        adressebeskyttelse = adressebeskyttelse,
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
                master = metadataMaster,
                opplysningsId = opplysningsId
            ),
            type = KontaktadresseType.Utland,
            utenlandskAdresse = utenlandskAdresse
        ),
        kontaktinformasjonForDoedsbo = null,
        utenlandskIdentifikasjonsnummer = listOf()
    )

}
