package no.nav.eessi.pensjon.pdl.identoppdatering

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.sed.PinItem
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.oppgave.OppgaveOppslag
import no.nav.eessi.pensjon.pdl.*
import no.nav.eessi.pensjon.pdl.OppgaveModel.*
import no.nav.eessi.pensjon.pdl.validering.LandspesifikkValidering
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.PersonoppslagException
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe.*
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.Npid
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource


class VurderIdentoppdateringTest : IdentBaseTest() {

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

        every { kodeverkClient.finnLandkode("NL") } returns "NLD"
        every { kodeverkClient.finnLandkode("NLD") } returns "NL"

        every { kodeverkClient.finnLandkode("BR") } returns "BGR"
        every { kodeverkClient.finnLandkode("BGR") } returns "BR"

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
            IngenOppdatering(
                "Ikke relevant for eessipensjon, buc: P_BUC_01, sed: X001, sektor: P",
                "Ikke relevant for eessipensjon"
            ),
            identoppdatering.vurderUtenlandskIdent(sedHendelse(
                sedType = SedType.X001,
                avsenderLand = "NO",
                navBruker = Fodselsnummer.fra(FNR)
            ))
        )
    }

    @Test
    fun `Gitt at bruker ikke har norsk pin i SED saa resulterer det i en NoUpdate`() {
        every { euxService.hentSed(any(), any()) } returns sed(pinItem = listOf(PinItem(identifikator = SVENSK_FNR, land = "SE")))

        assertEquals(
            IngenOppdatering("Bruker har ikke norsk pin i SED"),
            identoppdatering.vurderUtenlandskIdent(sedHendelse(avsenderLand = "SE", navBruker = Fodselsnummer.fra(FNR)))
        )
    }

    @Test
    fun `Gitt at det er en Sed uten utenlandsk ident saa blir det ingen oppdatering`() {
        every { euxService.hentSed(any(), any()) } returns sed(pinItem = listOf(PinItem(identifikator = FNR, land = "NO")))

        assertEquals(
            IngenOppdatering(description="Bruker har ikke utenlandsk ident fra avsenderland (SE)", metricTagValueOverride="Bruker har ikke utenlandsk ident fra avsenderland"),
            identoppdatering.vurderUtenlandskIdent(sedHendelse(avsenderLand = "SE", navBruker = Fodselsnummer.fra(FNR)))
        )
    }

    @Test
    fun `Gitt at Seden inneholder en uid der avsenderland ikke er det samme som uidland da blir det ingen oppdatering`() {
        every { euxService.hentSed(any(), any()) } returns sed(pinItem = listOf(PinItem(identifikator = FNR, land = "PL")))
        every { personService.hentPerson(NorskIdent(FNR)) } returns personFraPDL(id = FNR)

        assertEquals(
            IngenOppdatering(description="Bruker har ikke utenlandsk ident fra avsenderland (SE)", metricTagValueOverride="Bruker har ikke utenlandsk ident fra avsenderland"),
            identoppdatering.vurderUtenlandskIdent(sedHendelse(avsenderLand = "SE", navBruker = Fodselsnummer.fra(FNR)))
        )
    }

    @Test
    fun `Gitt at vi har en SedHendelse som mangler avsenderNavn saa skal vi faa en NoUpdate som resultat`() {

        every { euxService.hentSed(any(), any()) } returns sed(pinItem = listOf(PinItem(identifikator = FNR, land = "PL")))
        every { personService.hentPerson(NorskIdent(FNR)) } returns personFraPDL(id = FNR)

        assertEquals(
            IngenOppdatering("AvsenderNavn er ikke satt, kan derfor ikke lage endringsmelding"),
            identoppdatering.vurderUtenlandskIdent(sedHendelse(
                avsenderLand = "PL",
                avsenderNavn = null,
                navBruker = Fodselsnummer.fra(FNR)
            ))
        )
    }



    @Test
    fun `Gitt at SEDen inneholder en uid som ikke validerer saa blir det ingen oppdatering`() {

        every { euxService.hentSed(any(), any()) } returns
                sed(
                    pinItem = listOf(
                        PinItem(identifikator = "6549876543168765", land = "FI"),
                        PinItem(identifikator = FNR, land = "NO")
                    )
                )

        assertEquals(
            IngenOppdatering(
                description="Utenlandsk(e) id(-er) er ikke på gyldig format for land FI",
                metricTagValueOverride = "Utenlandsk id er ikke på gyldig format"
            ),
            identoppdatering.vurderUtenlandskIdent(sedHendelse(avsenderLand = "FI", navBruker = Fodselsnummer.fra(FNR)))
        )
    }

    @Test
    fun `Gitt at SEDen inneholder dupliserte uid'er og uid'er som ikke validerer så skal den likevel oppdatere den som validerer`() {

        every { euxService.hentSed(any(), any()) } returns
                sed(
                    pinItem = listOf(
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
            (identoppdatering.vurderUtenlandskIdent(sedHendelse(avsenderLand = "SE", navBruker = Fodselsnummer.fra(FNR))))
        )
    }

    @Test
    fun `Gitt at SEDen inneholder to danske på som varierer i format så skal det validere og oppdatere`() {

        every { euxService.hentSed(any(), any()) } returns
                sed(
                    pinItem = listOf(
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
                utstederland = "DNK",
                utenlandskInstitusjon = "Utenlandsk institusjon"
            )),
            (identoppdatering.vurderUtenlandskIdent(sedHendelse(avsenderLand = "DK", navBruker = Fodselsnummer.fra(FNR)))
        ))
    }

    @Test
    fun `Gitt at SEDen inneholder en Nederlandsk UID så skal vi formatere institusjonsnavn som validerer mot pdl og sende oppdatering`() {

        every { euxService.hentSed(any(), any()) } returns
                sed(
                    pinItem = listOf(
                        PinItem(identifikator = "1234.56.789", land = "NL", institusjonsnavn = "Employee Insurance UWV Amsterdam office:P_BUC_03 -> P_BUC_10, AW_BUC_06x, AW_BUC_07x, AW_BUC_9x, AW_BUC_10x, AW_BUC_11 -> AW_BUC_13, M_BUC_03b"),
                        PinItem(identifikator = FNR, land = "NO")
                    )
                )
        every { personService.hentPerson(NorskIdent(FNR)) } returns personFraPDL(id = FNR)

        assertEquals(
            Oppdatering("Innsending av endringsmelding", pdlEndringsMelding(
                FNR,
                utenlandskId = "1234.56.789",
                utstederland = "NLD",
                utenlandskInstitusjon = "Employee Insurance UWV Amsterdam office:P_BUC_03  P_BUC_10, AW_BUC_06x, AW_BUC_07x, AW_BUC_9x, AW_BUC_10x, AW_BUC_11  AW_BUC_13, M_BUC_03b"

            )),
            identoppdatering.vurderUtenlandskIdent(
                sedHendelse(
                    avsenderLand = "NL",
                    avsenderNavn = "Employee Insurance UWV Amsterdam office:P_BUC_03 -> P_BUC_10, AW_BUC_06x, AW_BUC_07x, AW_BUC_9x, AW_BUC_10x, AW_BUC_11  AW_BUC_13, M_BUC_03b",
                    navBruker = Fodselsnummer.fra(FNR)
                )
            )
        )
    }

    @Test
    fun `Gitt at SEDen inneholder en dansk UID på 10 siffer saa skal den validere og oppdatere PDL med riktig formatert UID ifht PDL-reglene`() {

        every { euxService.hentSed(any(), any()) } returns
                sed(
                    pinItem = listOf(
                    PinItem(identifikator = "1001772913", land = "DK"),
                    PinItem(identifikator = FNR, land = "NO")
            )
                )
        every { personService.hentPerson(NorskIdent(FNR)) } returns personFraPDL(id = FNR)

        assertEquals(
                Oppdatering("Innsending av endringsmelding", pdlEndringsMelding(
                    FNR,
                    utenlandskId = "100177-2913",
                    utstederland = "DNK",
                    utenlandskInstitusjon = "Utenlandsk institusjon"

                )),
                (identoppdatering.vurderUtenlandskIdent(sedHendelse(avsenderLand = "DK", navBruker = Fodselsnummer.fra(FNR)))
        ))
    }

    @Test
    fun `Gitt at SEDen inneholder en uid som er lik UID fra PDL saa blir det ingen oppdatering`() {

        every { personService.hentPerson(NorskIdent(FNR)) } returns personFraPDL(id = FNR)
            .copy(utenlandskIdentifikasjonsnummer = listOf(utenlandskIdentifikasjonsnummer(FINSK_FNR).copy(utstederland = "FIN")))
        every { euxService.hentSed(any(), any()) } returns
                sed(
                    pinItem = listOf(
                        PinItem(identifikator = FINSK_FNR, land = "FI"),
                        PinItem(identifikator = FNR, land = "NO")
                    )
                )

        assertEquals(
            IngenOppdatering("PDL uid er identisk med SED uid"),
            identoppdatering.vurderUtenlandskIdent(sedHendelse(avsenderLand = "FI", navBruker = Fodselsnummer.fra(FNR))
        ))
    }

    @Test
    fun `Gitt at pdl person ikke finnes og det kastes et PersonoppslagException saa skal det gi en NoUpdate`() {
        every { personService.hentPerson(NorskIdent(FNR)) } throws PersonoppslagException("pdl person finnes ikke", "not_found")
        every { euxService.hentSed(any(), any()) } returns
                sed(
                    pinItem = listOf(
                        PinItem(identifikator = FINSK_FNR, land = "FI"),
                        PinItem(identifikator = FNR, land = "NO")
                    )
                )

        assertEquals(
            IngenOppdatering("Finner ikke bruker i PDL med angitt fnr i SED"),
            identoppdatering.vurderUtenlandskIdent(sedHendelse(avsenderLand = "FI", navBruker = Fodselsnummer.fra(FNR))
        ))
    }

    @Test
    fun `Gitt at pdl person ikke finnes og det kastesen NPE saa skal den kastes videre`() {
        every { personService.hentPerson(NorskIdent(FNR)) } throws NullPointerException("pdl kaster NPE")
        every { euxService.hentSed(any(), any()) } returns
                sed(
                    pinItem = listOf(
                        PinItem(identifikator = FINSK_FNR, land = "FI"),
                        PinItem(identifikator = FNR, land = "NO")
                    )
                )

        assertThrows<NullPointerException> {
            identoppdatering.vurderUtenlandskIdent(sedHendelse(avsenderLand = "FI", navBruker = Fodselsnummer.fra(FNR)))
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
            IdentInformasjon(FNR, FOLKEREGISTERIDENT),
            IdentInformasjon(AKTOERID, AKTORID)
        ))
            .copy(utenlandskIdentifikasjonsnummer = listOf(utenlandskIdentifikasjonsnummer("510606-2234").copy(utstederland = "SWE")))

        every { euxService.hentSed(any(), any()) } returns
                sed(
                    pinItem = listOf(
                        PinItem(identifikator = uid, land = "SE"),
                        PinItem(identifikator = FNR, land = "NO")
                    )
                )

        assertEquals(
            IngenOppdatering("PDL uid er identisk med SED uid"),
            identoppdatering.vurderUtenlandskIdent(sedHendelse(avsenderLand = "SE", navBruker = Fodselsnummer.fra(FNR)))
        )
    }

    @Test
    fun `Gitt at SEDen inneholder uid som er feilformatert, men ulik UID fra PDL saa blir det oppgave`() {

        every { personService.hentPerson(NorskIdent(FNR)) } returns personFraPDL(id = FNR).copy(identer = listOf(
            IdentInformasjon(FNR, FOLKEREGISTERIDENT),
            IdentInformasjon(AKTOERID, AKTORID)
        ))
            .copy(utenlandskIdentifikasjonsnummer = listOf(utenlandskIdentifikasjonsnummer(SVENSK_FNR).copy(utstederland = "SWE")))

        every { euxService.hentSed(any(), any()) } returns
                sed(
                    pinItem = listOf(
                        PinItem(identifikator = "1951 06 06-22 34", land = "SE"),
                        PinItem(identifikator = FNR, land = "NO")
                    )
                )

        val sedHendelse = sedHendelse(avsenderLand = "SE", navBruker = Fodselsnummer.fra(FNR))
        every { oppgaveOppslag.finnesOppgavenAllerede(eq(sedHendelse.rinaSakId)) } returns false

        val actual = identoppdatering.vurderUtenlandskIdent(sedHendelse)

        assertTrue(actual is Oppgave)
        assertEquals(sedHendelse, (actual as Oppgave).oppgaveData.sedHendelse)
        assertEquals(AKTOERID, actual.oppgaveData.identifisertPerson.aktoerId)
    }

    @Test
    fun `Gitt at SEDen inneholder uid som er ulik UID fra PDL men det finnes allerede en oppgave på det fra foer av`() {

        every { personService.hentPerson(NorskIdent(FNR)) } returns personFraPDL(id = FNR).copy(identer = listOf(
            IdentInformasjon(FNR, FOLKEREGISTERIDENT),
            IdentInformasjon(AKTOERID, AKTORID)
        ))
            .copy(utenlandskIdentifikasjonsnummer = listOf(utenlandskIdentifikasjonsnummer(SVENSK_FNR).copy(utstederland = "SWE")))

        every { euxService.hentSed(any(), any()) } returns
                sed(
                    pinItem = listOf(
                        PinItem(identifikator = "5 12 020-2234", land = "SE"),
                        PinItem(identifikator = FNR, land = "NO")
                    )
                )

        val sedHendelse = sedHendelse(avsenderLand = "SE", navBruker = Fodselsnummer.fra(FNR))
        every { oppgaveOppslag.finnesOppgavenAllerede(eq(sedHendelse.rinaSakId)) } returns true

        assertEquals(
            IngenOppdatering("Oppgave opprettet tidligere"),
            identoppdatering.vurderUtenlandskIdent(sedHendelse)
        )
    }

    @Test
    fun `Gitt at SEDen inneholder uid som er ulik UID fra PDL saa skal vi oppdatere`() {


        every { personService.hentPerson(NorskIdent(FNR)) } returns personFraPDL(id = FNR).copy(identer = listOf(
            IdentInformasjon(FNR, FOLKEREGISTERIDENT),
            IdentInformasjon(AKTOERID, AKTORID)
        ))
            .copy(utenlandskIdentifikasjonsnummer = listOf(utenlandskIdentifikasjonsnummer(SVENSK_FNR).copy(utstederland = "SWE")))

        every { euxService.hentSed(any(), any()) } returns
                sed(
                    pinItem = listOf(
                        PinItem(identifikator = "5 12 020-2234", land = "SE"),
                        PinItem(identifikator = FNR, land = "NO")
                    )
                )

        val sedHendelse = sedHendelse(avsenderLand = "SE", navBruker = Fodselsnummer.fra(FNR))
        every { oppgaveOppslag.finnesOppgavenAllerede(eq(sedHendelse.rinaSakId)) } returns false

        val actual = identoppdatering.vurderUtenlandskIdent(sedHendelse)

        assertTrue(actual is Oppgave)
        assertEquals(sedHendelse, (actual as Oppgave).oppgaveData.sedHendelse)
        assertEquals(AKTOERID, actual.oppgaveData.identifisertPerson.aktoerId)
    }

    @Test
    fun `Gitt at SEDen inneholder uid som er ulik UID fra PDL saa skal vi oppdatere for bruker med Npid`() {
        val npid = "01220049651"

        every { personService.hentPerson(Npid(npid)) } returns personFraPDL(id = npid).copy(identer = listOf(
            IdentInformasjon(npid, NPID),
            IdentInformasjon(AKTOERID, AKTORID)
        ))
            .copy(utenlandskIdentifikasjonsnummer = listOf(utenlandskIdentifikasjonsnummer(SVENSK_FNR).copy(utstederland = "SWE")))

        every { euxService.hentSed(any(), any()) } returns
                sed(
                    pinItem = listOf(
                        PinItem(identifikator = "5 12 020-2234", land = "SE"),
                        PinItem(identifikator = npid, land = "NO")
                    )
                )

        val sedHendelse = sedHendelse(avsenderLand = "SE", navBruker = Fodselsnummer.fra(FNR))
        every { oppgaveOppslag.finnesOppgavenAllerede(eq(sedHendelse.rinaSakId)) } returns false

        val actual = identoppdatering.vurderUtenlandskIdent(sedHendelse)

        assertTrue(actual is Oppgave)
        assertEquals(sedHendelse, (actual as Oppgave).oppgaveData.sedHendelse)
        assertEquals(AKTOERID, actual.oppgaveData.identifisertPerson.aktoerId)
    }

    @Test
    fun `Gitt at vi har en endringsmelding med en svensk uid, med riktig format saa skal det opprettes en endringsmelding`() {
        every { personService.hentPerson(NorskIdent(FNR)) } returns
                personFraPDL(id = FNR).copy(identer = listOf(IdentInformasjon(FNR, FOLKEREGISTERIDENT)))

        every { euxService.hentSed(any(), any()) } returns
                sed(
                    pinItem = listOf(
                        PinItem(identifikator = "5 12 020-1234", land = "SE"),
                        PinItem(identifikator = FNR, land = "NO")
                    )
                )

        assertEquals(
            Oppdatering("Innsending av endringsmelding", pdlEndringsMelding(FNR, utstederland = "SWE")),
            (identoppdatering.vurderUtenlandskIdent(sedHendelse(avsenderLand = "SE", navBruker = Fodselsnummer.fra(FNR)))
        ))

    }

    @Test
    fun `Gitt at vi har en endringsmelding med en bulgarsk uid, med mellomrom i saa skal det opprettes en endringsmelding`() {
        every { personService.hentPerson(NorskIdent(FNR)) } returns
                personFraPDL(id = FNR).copy(identer = listOf(IdentInformasjon(FNR, FOLKEREGISTERIDENT)))

        every { euxService.hentSed(any(), any()) } returns
                sed(
                    pinItem = listOf(
                        PinItem(identifikator = "5 12 020  1234", land = "BR"),
                        PinItem(identifikator = FNR, land = "NO")
                    )
                )

        assertEquals(
            Oppdatering("Innsending av endringsmelding", pdlEndringsMelding(FNR, utenlandskId = "5120201234", utstederland = "BGR")),
            (identoppdatering.vurderUtenlandskIdent(sedHendelse(avsenderLand = "BR", navBruker = Fodselsnummer.fra(FNR))))
        )

    }

    @Test
    fun `Gitt at vi har en SED med svensk UID naar det allerede finnes en UID i PDL fra Finland saa skal det opprettes en endringsmelding`() {
        every { personService.hentPerson(NorskIdent(FNR)) } returns
                personFraPDL(id = FNR).copy(identer = listOf(IdentInformasjon(FNR, FOLKEREGISTERIDENT)))
                    .copy(utenlandskIdentifikasjonsnummer = listOf(utenlandskIdentifikasjonsnummer(FINSK_FNR).copy(utstederland = "FIN")))

        every { euxService.hentSed(any(), any()) } returns
                sed(
                    pinItem = listOf(
                        PinItem(identifikator = "5 12 020-1234", land = "SE"),
                        PinItem(identifikator = FNR, land = "NO")
                    )
                )

        assertEquals(
            Oppdatering("Innsending av endringsmelding", pdlEndringsMelding(FNR, utstederland = "SWE")),
            identoppdatering.vurderUtenlandskIdent(sedHendelse(avsenderLand = "SE", navBruker = Fodselsnummer.fra(FNR)))
        )

    }

    @Test
    fun `Gitt at vi har en SED med norsk fnr som skal oppdateres til pdl, der PDL har en aktoerid inne i PDL saa skal Oppdateringsmeldingen til PDL ha norsk FNR og ikke aktoerid`() {

        every { personService.hentPerson(NorskIdent(FNR)) } returns
                personFraPDL(id = FNR).copy(identer = listOf(IdentInformasjon("1234567891234", AKTORID), IdentInformasjon(FNR, FOLKEREGISTERIDENT)))
                        .copy(utenlandskIdentifikasjonsnummer = listOf(utenlandskIdentifikasjonsnummer(FINSK_FNR).copy(utstederland = "FIN")))

        every { euxService.hentSed(any(), any()) } returns
                sed(
                    pinItem = listOf(
                        PinItem(identifikator = "5 12 020-1234", land = "SE"),
                        PinItem(identifikator = FNR, land = "NO")
                    )
                )

        assertEquals(
                Oppdatering("Innsending av endringsmelding", pdlEndringsMelding(FNR, utstederland = "SWE")),
                identoppdatering.vurderUtenlandskIdent(sedHendelse(avsenderLand = "SE", navBruker = Fodselsnummer.fra(FNR)))
        )

    }

    @Test
    fun `Gitt at vi har en SED med norsk dnr som skal oppdateres til pdl, der PDL har et fnr inne så skal Oppdateringsmeldingen til PDL ha norsk FNR og ikke dnr`() {
        every { personService.hentPerson(NorskIdent(DNR)) } returns
                personFraPDL(id = DNR).copy(identer = listOf(IdentInformasjon(FNR, FOLKEREGISTERIDENT)))
                    .copy(utenlandskIdentifikasjonsnummer = listOf(utenlandskIdentifikasjonsnummer(FINSK_FNR).copy(utstederland = "FIN")))

        every { euxService.hentSed(any(), any()) } returns
                sed(
                    pinItem = listOf(
                        PinItem(identifikator = "5 12 020-1234", land = "SE"),
                        PinItem(identifikator = DNR, land = "NO")
                    )
                )

        assertEquals(
            Oppdatering("Innsending av endringsmelding", pdlEndringsMelding(FNR, utstederland = "SWE")),
            identoppdatering.vurderUtenlandskIdent(sedHendelse(avsenderLand = "SE", navBruker = Fodselsnummer.fra(DNR)))
        )
    }

    @Test
    fun `Gitt at vi har en SED med npid som skal oppdateres til pdl, der PDL har et fnr inne så skal Oppdateringsmeldingen til PDL ha norsk FNR og ikke dnr`() {
        val npid = "01220049651"
        every { personService.hentPerson(Npid(npid)) } returns
                personFraPDL(id = npid).copy(identer = listOf(IdentInformasjon(npid, NPID)))
                    .copy(utenlandskIdentifikasjonsnummer = listOf(utenlandskIdentifikasjonsnummer(FINSK_FNR).copy(utstederland = "FIN")))

        every { euxService.hentSed(any(), any()) } returns
                sed(
                    pinItem = listOf(
                        PinItem(identifikator = "5 12 020-1234", land = "SE"),
                        PinItem(identifikator = npid, land = "NO")
                    )
                )

        assertEquals(
            Oppdatering("Innsending av endringsmelding", pdlEndringsMelding(npid, utstederland = "SWE")),
            identoppdatering.vurderUtenlandskIdent(sedHendelse(avsenderLand = "SE", navBruker = Fodselsnummer.fra(npid)))
        )
    }
}