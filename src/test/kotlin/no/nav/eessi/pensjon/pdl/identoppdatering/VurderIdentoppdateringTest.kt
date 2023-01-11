package no.nav.eessi.pensjon.pdl.identoppdatering

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.eux.model.buc.BucType.*
import no.nav.eessi.pensjon.eux.model.sed.*
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.models.EndringsmeldingUID
import no.nav.eessi.pensjon.models.PdlEndringOpplysning
import no.nav.eessi.pensjon.models.Personopplysninger
import no.nav.eessi.pensjon.oppgave.OppgaveOppslag
import no.nav.eessi.pensjon.pdl.identoppdatering.VurderIdentoppdatering.*
import no.nav.eessi.pensjon.pdl.validering.LandspesifikkValidering
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import no.nav.eessi.pensjon.personidentifisering.SEDPersonRelasjon
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.PersonoppslagException
import no.nav.eessi.pensjon.personoppslag.pdl.model.*
import no.nav.eessi.pensjon.personoppslag.pdl.model.Person
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate
import java.time.LocalDateTime

private const val FNR = "11067122781"
private const val DNR = "51077403071"
private const val SVENSK_FNR = "512020-1234"
private const val FINSK_FNR = "130177-308T"
private const val SOME_FNR = "1234567799"
private const val AKTOERID = "32165498732"

private class VurderIdentoppdateringTest {

    var euxService: EuxService = mockk(relaxed = true)
    var kodeverkClient: KodeverkClient = mockk(relaxed = true)
    var oppgaveOppslag: OppgaveOppslag = mockk()
    var personService: PersonService = mockk()
    var landspesifikkValidering = LandspesifikkValidering(kodeverkClient)
    lateinit var identoppdatering : VurderIdentoppdatering

    @BeforeEach
    fun setup() {
        every { kodeverkClient.finnLandkode("PL") } returns "POL"
        every { kodeverkClient.finnLandkode("POL") } returns "PL"

        every { kodeverkClient.finnLandkode("SE") } returns "SWE"
        every { kodeverkClient.finnLandkode("SWE") } returns "SE"

        every { kodeverkClient.finnLandkode("DK") } returns "DNK"
        every { kodeverkClient.finnLandkode("DNK") } returns "DK"

        every { kodeverkClient.finnLandkode("FI") } returns "FIN"
        every { kodeverkClient.finnLandkode("FIN") } returns "FI"

        identoppdatering = VurderIdentoppdatering(
            euxService,
            oppgaveOppslag,
            kodeverkClient,
            personService,
            landspesifikkValidering
        )

    }

    @Test
    fun `Gitt at sed som ikke er relevant for eessipensjon saa gjoeres ingen oppdatering`() {

        assertEquals(
            IngenOppdatering("Ikke relevant for eessipensjon, buc: P_BUC_01, sed: X001, sektor: P", "Ikke relevant for eessipensjon"),
            identoppdatering.vurderUtenlandskIdent(sedHendelse(avsenderLand = "NO", sedType = SedType.X001))
        )
    }

    @Test
    fun `Gitt at bruker ikke har norsk pin i SED saa resulterer det i en NoUpdate`() {
        every { euxService.hentSed(any(), any()) } returns sed(id = SVENSK_FNR, land = "SE")

        assertEquals(
            IngenOppdatering("Bruker har ikke norsk pin i SED"),
            identoppdatering.vurderUtenlandskIdent(sedHendelse(avsenderLand = "SE"))
        )
    }

    @Test
    fun `Gitt at det er en Sed uten utenlandsk ident saa blir det ingen oppdatering`() {
        every { euxService.hentSed(any(), any()) } returns sed(id = FNR, land = "NO")

        assertEquals(
            IngenOppdatering(description="Bruker har ikke utenlandsk ident fra avsenderland (SE)", metricTagValueOverride="Bruker har ikke utenlandsk ident fra avsenderland"),
            identoppdatering.vurderUtenlandskIdent(sedHendelse(avsenderLand = "SE"))
        )
    }

    @Test
    fun `Gitt at Seden inneholder en uid der avsenderland ikke er det samme som uidland da blir det ingen oppdatering`() {
        every { euxService.hentSed(any(), any()) } returns sed(id = FNR, land = "PL")
        every { personService.hentPerson(NorskIdent(FNR)) } returns personFraPDL(id = FNR)

        assertEquals(
            IngenOppdatering(description="Bruker har ikke utenlandsk ident fra avsenderland (SE)", metricTagValueOverride="Bruker har ikke utenlandsk ident fra avsenderland"),
            identoppdatering.vurderUtenlandskIdent(sedHendelse(avsenderLand = "SE"))
        )
    }

    @Test
    fun `Gitt at vi har en SedHendelse som mangler avsenderNavn saa skal vi faa en NoUpdate som resultat`() {

        every { euxService.hentSed(any(), any()) } returns sed(id = FNR, land = "PL")
        every { personService.hentPerson(NorskIdent(FNR)) } returns personFraPDL(id = FNR)

        assertEquals(
            IngenOppdatering("AvsenderNavn er ikke satt, kan derfor ikke lage endringsmelding"),
            identoppdatering.vurderUtenlandskIdent(sedHendelse(avsenderLand = "PL", avsenderNavn = null))
        )
    }



    @Test
    fun `Gitt at SEDen inneholder en uid som ikke validerer saa blir det ingen oppdatering`() {

        every { euxService.hentSed(any(), any()) } returns
                sed(
                    id = FNR, land = "NO", pinItem = listOf(
                        PinItem(identifikator = "6549876543168765", land = "FI"),
                        PinItem(identifikator = FNR, land = "NO")
                    )
                )

        assertEquals(
            IngenOppdatering(
                description="Utenlandsk(e) id(-er) er ikke på gyldig format for land FI",
                metricTagValueOverride = "Utenlandsk id er ikke på gyldig format"
            ),
            identoppdatering.vurderUtenlandskIdent(sedHendelse(avsenderLand = "FI"))
        )
    }

    @Test
    fun `Gitt at SEDen inneholder dupliserte uid'er og uid'er som ikke validerer så skal den likevel oppdatere den som validerer`() {

        every { euxService.hentSed(any(), any()) } returns
                sed(
                    id = FNR, land = "NO", pinItem = listOf(
                        PinItem(identifikator = "6549876543168765", land = "SE"), // validerer ikke
                        PinItem(identifikator = SVENSK_FNR, land = "SE"), // validerer
                        PinItem(identifikator = SVENSK_FNR, land = "SE"), // validerer
                        PinItem(identifikator = "1234567890", land = "SE"), // validerer også
                        PinItem(identifikator = FNR, land = "NO")
                    )
                )
        every { personService.hentPerson(NorskIdent(FNR)) } returns personFraPDL(id = FNR)


        assertEquals(
            Oppdatering("Innsending av endringsmelding", pdlEndringsMelding(FNR, utstederland = "SWE")),
            (identoppdatering.vurderUtenlandskIdent(sedHendelse(avsenderLand = "SE")))
        )
    }

    @Test
    fun `Gitt at SEDen inneholder to danske på som varierer i format så skal det validere og oppdatere`() {

        every { euxService.hentSed(any(), any()) } returns
                sed(
                    id = FNR, land = "NO", pinItem = listOf(
                        PinItem(identifikator = "10017729135", land = "DK"),
                        PinItem(identifikator = "100177-2913", land = "DK"),
                        PinItem(identifikator = FNR, land = "NO")
                    )
                )
        every { personService.hentPerson(NorskIdent(FNR)) } returns personFraPDL(id = FNR)

        assertEquals(
            Oppdatering("Innsending av endringsmelding", pdlEndringsMelding(
                FNR,
                utenlandskId = "100177-2913",
                utstederland = "DNK"
            )),
            (identoppdatering.vurderUtenlandskIdent(sedHendelse(avsenderLand = "DK")))
        )
    }

    @Test
    fun `Gitt at SEDen inneholder en dansk UID på 10 siffer saa skal den validere og oppdatere PDL med riktig formatert UID ifht PDL-reglene`() {

        every { euxService.hentSed(any(), any()) } returns
                sed(
                        id = FNR, land = "NO", pinItem = listOf(
                        PinItem(identifikator = "1001772913", land = "DK"),
                        PinItem(identifikator = FNR, land = "NO")
                )
                )
        every { personService.hentPerson(NorskIdent(FNR)) } returns personFraPDL(id = FNR)

        assertEquals(
                Oppdatering("Innsending av endringsmelding", pdlEndringsMelding(
                        FNR,
                        utenlandskId = "100177-2913",
                        utstederland = "DNK"
                )),
                (identoppdatering.vurderUtenlandskIdent(sedHendelse(avsenderLand = "DK")))
        )
    }

    @Test
    fun `Gitt at SEDen inneholder en uid som er lik UID fra PDL saa blir det ingen oppdatering`() {

        every { personService.hentPerson(NorskIdent(FNR)) } returns personFraPDL(id = FNR)
            .copy(utenlandskIdentifikasjonsnummer = listOf(utenlandskIdentifikasjonsnummer(FINSK_FNR).copy(utstederland = "FIN")))
        every { euxService.hentSed(any(), any()) } returns
                sed(
                    id = FNR, land = "NO", pinItem = listOf(
                        PinItem(identifikator = FINSK_FNR, land = "FI"),
                        PinItem(identifikator = FNR, land = "NO")
                    )
                )

        assertEquals(
            IngenOppdatering("PDL uid er identisk med SED uid"),
            identoppdatering.vurderUtenlandskIdent(sedHendelse(avsenderLand = "FI"))
        )
    }

    @Test
    fun `Gitt at pdl person ikke finnes og det kastes et PersonoppslagException saa skal det gi en NoUpdate`() {
        every { personService.hentPerson(NorskIdent(FNR)) } throws PersonoppslagException("pdl person finnes ikke", "not_found")
        every { euxService.hentSed(any(), any()) } returns
                sed(
                    id = FNR, land = "NO", pinItem = listOf(
                        PinItem(identifikator = FINSK_FNR, land = "FI"),
                        PinItem(identifikator = FNR, land = "NO")
                    )
                )

        assertEquals(
            IngenOppdatering("Finner ikke bruker i PDL med angitt fnr i SED"),
            identoppdatering.vurderUtenlandskIdent(sedHendelse(avsenderLand = "FI"))
        )
    }

    @Test
    fun `Gitt at pdl person ikke finnes og det kastesen NPE saa skal den kastes videre`() {
        every { personService.hentPerson(NorskIdent(FNR)) } throws NullPointerException("pdl kaster NPE")
        every { euxService.hentSed(any(), any()) } returns
                sed(
                    id = FNR, land = "NO", pinItem = listOf(
                        PinItem(identifikator = FINSK_FNR, land = "FI"),
                        PinItem(identifikator = FNR, land = "NO")
                    )
                )

        assertThrows<NullPointerException> {
            identoppdatering.vurderUtenlandskIdent(sedHendelse(avsenderLand = "FI"))
        }
    }

    @ParameterizedTest
    @CsvSource(
        "51 06 06-22 34",
        "19 51 06 06-22 34",
        "19 510606-2234",
        "19 510606 2234"
    )
    fun `Gitt at SEDen inneholder uid som er feilformatert men lik UID fra PDL saa blir det ingen oppdatering`(uid : String) {

        every { personService.hentPerson(NorskIdent(FNR)) } returns personFraPDL(id = FNR).copy(identer = listOf(
            IdentInformasjon(FNR, IdentGruppe.FOLKEREGISTERIDENT),
            IdentInformasjon(AKTOERID, IdentGruppe.AKTORID)
        ))
            .copy(utenlandskIdentifikasjonsnummer = listOf(utenlandskIdentifikasjonsnummer("510606-2234").copy(utstederland = "SWE")))

        every { euxService.hentSed(any(), any()) } returns
                sed(
                    id = FNR, land = "NO", pinItem = listOf(
                        PinItem(identifikator = uid, land = "SE"),
                        PinItem(identifikator = FNR, land = "NO")
                    )
                )

        assertEquals(
            IngenOppdatering("PDL uid er identisk med SED uid"),
            identoppdatering.vurderUtenlandskIdent(sedHendelse(avsenderLand = "SE"))
        )
    }

    @Test
    fun `Gitt at SEDen inneholder uid som er feilformatert, men ulik UID fra PDL saa blir det oppgave`() {

        every { personService.hentPerson(NorskIdent(FNR)) } returns personFraPDL(id = FNR).copy(identer = listOf(
            IdentInformasjon(FNR, IdentGruppe.FOLKEREGISTERIDENT),
            IdentInformasjon(AKTOERID, IdentGruppe.AKTORID)
        ))
            .copy(utenlandskIdentifikasjonsnummer = listOf(utenlandskIdentifikasjonsnummer(SVENSK_FNR).copy(utstederland = "SWE")))

        every { euxService.hentSed(any(), any()) } returns
                sed(
                    id = FNR, land = "NO", pinItem = listOf(
                        PinItem(identifikator = "1951 06 06-22 34", land = "SE"),
                        PinItem(identifikator = FNR, land = "NO")
                    )
                )

        val sedHendelse = sedHendelse(avsenderLand = "SE")
        every { oppgaveOppslag.finnesOppgavenAllerede(eq(sedHendelse.rinaSakId)) } returns false

        val actual = identoppdatering.vurderUtenlandskIdent(sedHendelse)

        assertTrue(actual is Oppgave)
        assertEquals(sedHendelse, (actual as Oppgave).oppgaveData.sedHendelse)
        assertEquals(AKTOERID, actual.oppgaveData.identifisertPerson.aktoerId)
    }

    @Test
    fun `Gitt at SEDen inneholder uid som er ulik UID fra PDL men det finnes allerede en oppgave på det fra foer av`() {

        every { personService.hentPerson(NorskIdent(FNR)) } returns personFraPDL(id = FNR).copy(identer = listOf(
            IdentInformasjon(FNR, IdentGruppe.FOLKEREGISTERIDENT),
            IdentInformasjon(AKTOERID, IdentGruppe.AKTORID)
        ))
            .copy(utenlandskIdentifikasjonsnummer = listOf(utenlandskIdentifikasjonsnummer(SVENSK_FNR).copy(utstederland = "SWE")))

        every { euxService.hentSed(any(), any()) } returns
                sed(
                    id = FNR, land = "NO", pinItem = listOf(
                        PinItem(identifikator = "5 12 020-2234", land = "SE"),
                        PinItem(identifikator = FNR, land = "NO")
                    )
                )

        val sedHendelse = sedHendelse(avsenderLand = "SE")
        every { oppgaveOppslag.finnesOppgavenAllerede(eq(sedHendelse.rinaSakId)) } returns true

        assertEquals(
            IngenOppdatering("Oppgave opprettet tidligere"),
            identoppdatering.vurderUtenlandskIdent(sedHendelse)
        )
    }

    @Test
    fun `Gitt at SEDen inneholder uid som er ulik UID fra PDL saa skal vi oppdatere`() {


        every { personService.hentPerson(NorskIdent(FNR)) } returns personFraPDL(id = FNR).copy(identer = listOf(
            IdentInformasjon(FNR, IdentGruppe.FOLKEREGISTERIDENT),
            IdentInformasjon(AKTOERID, IdentGruppe.AKTORID)
        ))
            .copy(utenlandskIdentifikasjonsnummer = listOf(utenlandskIdentifikasjonsnummer(SVENSK_FNR).copy(utstederland = "SWE")))

        every { euxService.hentSed(any(), any()) } returns
                sed(
                    id = FNR, land = "NO", pinItem = listOf(
                        PinItem(identifikator = "5 12 020-2234", land = "SE"),
                        PinItem(identifikator = FNR, land = "NO")
                    )
                )

        val sedHendelse = sedHendelse(avsenderLand = "SE")
        every { oppgaveOppslag.finnesOppgavenAllerede(eq(sedHendelse.rinaSakId)) } returns false

        val actual = identoppdatering.vurderUtenlandskIdent(sedHendelse)

        assertTrue(actual is Oppgave)
        assertEquals(sedHendelse, (actual as Oppgave).oppgaveData.sedHendelse)
        assertEquals(AKTOERID, actual.oppgaveData.identifisertPerson.aktoerId)
    }

    @Test
    fun `Gitt at vi har en endringsmelding med en svensk uid, med riktig format saa skal det opprettes en endringsmelding`() {
        every { personService.hentPerson(NorskIdent(FNR)) } returns
                personFraPDL(id = FNR).copy(identer = listOf(IdentInformasjon(FNR, IdentGruppe.FOLKEREGISTERIDENT)))

        every { euxService.hentSed(any(), any()) } returns
                sed(
                    id = FNR, land = "NO", pinItem = listOf(
                        PinItem(identifikator = "5 12 020-1234", land = "SE"),
                        PinItem(identifikator = FNR, land = "NO")
                    )
                )

        assertEquals(
            Oppdatering("Innsending av endringsmelding", pdlEndringsMelding(FNR, utstederland = "SWE")),
            (identoppdatering.vurderUtenlandskIdent(sedHendelse(avsenderLand = "SE")))
        )

    }

    @Test
    fun `Gitt at vi har en SED med svensk UID naar det allerede finnes en UID i PDL fra Finland saa skal det opprettes en endringsmelding`() {
        every { personService.hentPerson(NorskIdent(FNR)) } returns
                personFraPDL(id = FNR).copy(identer = listOf(IdentInformasjon(FNR, IdentGruppe.FOLKEREGISTERIDENT)))
                    .copy(utenlandskIdentifikasjonsnummer = listOf(utenlandskIdentifikasjonsnummer(FINSK_FNR).copy(utstederland = "FIN")))

        every { euxService.hentSed(any(), any()) } returns
                sed(
                    id = FNR, land = "NO", pinItem = listOf(
                        PinItem(identifikator = "5 12 020-1234", land = "SE"),
                        PinItem(identifikator = FNR, land = "NO")
                    )
                )

        assertEquals(
            Oppdatering("Innsending av endringsmelding", pdlEndringsMelding(FNR, utstederland = "SWE")),
            identoppdatering.vurderUtenlandskIdent(sedHendelse(avsenderLand = "SE"))
        )

    }

    @Test
    fun `Gitt at vi har en SED med norsk fnr som skal oppdateres til pdl, der PDL har en aktoerid inne i PDL saa skal Oppdateringsmeldingen til PDL ha norsk FNR og ikke aktoerid`() {

        every { personService.hentPerson(NorskIdent(FNR)) } returns
                personFraPDL(id = FNR).copy(identer = listOf(IdentInformasjon("1234567891234", IdentGruppe.AKTORID), IdentInformasjon(FNR, IdentGruppe.FOLKEREGISTERIDENT)))
                        .copy(utenlandskIdentifikasjonsnummer = listOf(utenlandskIdentifikasjonsnummer(FINSK_FNR).copy(utstederland = "FIN")))

        every { euxService.hentSed(any(), any()) } returns
                sed(
                    id = FNR, land = "NO", pinItem = listOf(
                        PinItem(identifikator = "5 12 020-1234", land = "SE"),
                        PinItem(identifikator = FNR, land = "NO")
                    )
                )

        assertEquals(
                Oppdatering("Innsending av endringsmelding", pdlEndringsMelding(FNR, utstederland = "SWE")),
                identoppdatering.vurderUtenlandskIdent(sedHendelse(avsenderLand = "SE"))
        )

    }

    @Test
    fun `Gitt at vi har en SED med norsk dnr som skal oppdateres til pdl, der PDL har et fnr inne så skal Oppdateringsmeldingen til PDL ha norsk FNR og ikke dnr`() {
        every { personService.hentPerson(NorskIdent(DNR)) } returns
                personFraPDL(id = FNR).copy(identer = listOf(IdentInformasjon(FNR, IdentGruppe.FOLKEREGISTERIDENT)))
                        .copy(utenlandskIdentifikasjonsnummer = listOf(utenlandskIdentifikasjonsnummer(FINSK_FNR).copy(utstederland = "FIN")))

        every { euxService.hentSed(any(), any()) } returns
                sed(
                    id = DNR, land = "NO", pinItem = listOf(
                        PinItem(identifikator = "5 12 020-1234", land = "SE"),
                        PinItem(identifikator = DNR, land = "NO")
                    )
                )

        assertEquals(
                Oppdatering("Innsending av endringsmelding", pdlEndringsMelding(FNR, utstederland = "SWE")),
                identoppdatering.vurderUtenlandskIdent(sedHendelse(avsenderLand = "SE"))
        )
    }

    private fun pdlEndringsMelding(
        fnr: String,
        utenlandskId: String = SVENSK_FNR,
        utstederland: String,
        utenlandskInstitusjon: String = "Utenlandsk institusjon"
    ) =
        PdlEndringOpplysning(
            listOf(
                Personopplysninger(
                    endringstype = Endringstype.OPPRETT,
                    ident = fnr,
                    endringsmelding = EndringsmeldingUID(
                        identifikasjonsnummer = utenlandskId,
                        utstederland = utstederland,
                        kilde = utenlandskInstitusjon
                    ),
                    opplysningstype = Opplysningstype.UTENLANDSKIDENTIFIKASJONSNUMMER
                )
            )
        )

    private fun identifisertPerson(
        erDoed: Boolean = false, uidFraPdl: List<UtenlandskIdentifikasjonsnummer> = emptyList()
    ) = IdentifisertPerson(
        fnr = Fodselsnummer.fra(FNR),
        uidFraPdl = uidFraPdl,
        aktoerId = "123456789351",
        landkode = null,
        geografiskTilknytning = null,
        harAdressebeskyttelse = erDoed,
        personListe = null,
        personRelasjon = SEDPersonRelasjon(
            relasjon = Relasjon.ANNET,
            fnr = Fodselsnummer.fra(FNR),
            rinaDocumentId = "12345"
        ),
        erDoed = erDoed,
        kontaktAdresse = null,
    )

    private fun utenlandskIdentifikasjonsnummer(fnr: String) = UtenlandskIdentifikasjonsnummer(
        identifikasjonsnummer = fnr,
        utstederland = "POL",
        opphoert = false,
        folkeregistermetadata = Folkeregistermetadata(
            gyldighetstidspunkt = LocalDateTime.now(),
            ajourholdstidspunkt = LocalDateTime.now(),
        ),
        metadata = metadata()
    )

    private fun metadata() = Metadata(
        endringer = emptyList(),
        historisk = false,
        master = "PDL",
        opplysningsId = "opplysningsId"
    )

    private fun sedHendelse(
        bucType: BucType = P_BUC_01,
        sedType: SedType = SedType.P2000,
        avsenderLand: String = "SE",
        avsenderNavn: String? = "Utenlandsk institusjon"
    ) = SedHendelse(
        sektorKode = "P",
        avsenderLand = avsenderLand,
        bucType = bucType,
        rinaSakId = "123456479867",
        rinaDokumentId = "SOME_DOKUMENT_ID",
        rinaDokumentVersjon = "SOME_RINADOKUMENT_VERSION",
        sedType = sedType,
        avsenderNavn = avsenderNavn
    )

    private fun sed(id: String = SOME_FNR,
                     brukersAdresse: Adresse? = null,
                     land: String?,
                     pinItem: List<PinItem>? = listOf(PinItem(land = land, identifikator = id)) ) = SED(
        type = SedType.P2000,
        sedGVer = null,
        sedVer = null,
        nav = Nav(
            eessisak = null,
            bruker = Bruker(
                mor = null,
                far = null,
                person = no.nav.eessi.pensjon.eux.model.sed.Person(
                    pin = pinItem,
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
        id: String = SOME_FNR,
        adressebeskyttelse: List<AdressebeskyttelseGradering> = listOf(),
        utenlandskAdresse: UtenlandskAdresse? = null,
        opplysningsId: String = "DummyOpplysningsId",
        gyldigFraOgMed: LocalDateTime = LocalDateTime.now().minusDays(10),
        gyldigTilOgMed: LocalDateTime = LocalDateTime.now().plusDays(10),
        doedsdato: LocalDate? = null
    ): Person = Person(
        identer = listOf(IdentInformasjon(id, IdentGruppe.FOLKEREGISTERIDENT)),
        navn = null,
        adressebeskyttelse = adressebeskyttelse,
        bostedsadresse = null,
        oppholdsadresse = null,
        statsborgerskap = listOf(),
        foedsel = null,
        geografiskTilknytning = null,
        kjoenn = null,
        doedsfall = Doedsfall(
            doedsdato = doedsdato,
            folkeregistermetadata = null,
            metadata= metadata()
        ),
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