/*
//package no.nav.eessi.pensjon.pdl.identoppdatering
//
//import io.mockk.every
//import io.mockk.mockk
//import no.nav.eessi.pensjon.eux.EuxService
//import no.nav.eessi.pensjon.eux.UtenlandskId
//import no.nav.eessi.pensjon.eux.UtenlandskPersonIdentifisering
//import no.nav.eessi.pensjon.eux.model.SedType
//import no.nav.eessi.pensjon.eux.model.buc.BucType
//import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
//import no.nav.eessi.pensjon.eux.model.document.SedStatus
//import no.nav.eessi.pensjon.eux.model.sed.Adresse
//import no.nav.eessi.pensjon.eux.model.sed.Bruker
//import no.nav.eessi.pensjon.eux.model.sed.Nav
//import no.nav.eessi.pensjon.eux.model.sed.Pensjon
//import no.nav.eessi.pensjon.eux.model.sed.Person
//import no.nav.eessi.pensjon.eux.model.sed.PinItem
//import no.nav.eessi.pensjon.eux.model.sed.SED
//import no.nav.eessi.pensjon.kodeverk.KodeverkClient
//import no.nav.eessi.pensjon.models.SedHendelse
//import no.nav.eessi.pensjon.oppgave.OppgaveHandler
//import no.nav.eessi.pensjon.pdl.filtrering.PdlFiltrering
//import no.nav.eessi.pensjon.pdl.identoppdatering.IdentOppdatering.NoUpdate
//import no.nav.eessi.pensjon.pdl.identoppdatering.IdentOppdatering.Update
//import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
//import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
//import no.nav.eessi.pensjon.personidentifisering.Relasjon
//import no.nav.eessi.pensjon.personidentifisering.SEDPersonRelasjon
//import no.nav.eessi.pensjon.personoppslag.Fodselsnummer.Companion.fra
//import no.nav.eessi.pensjon.personoppslag.pdl.model.Folkeregistermetadata
//import no.nav.eessi.pensjon.personoppslag.pdl.model.Metadata
//import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskIdentifikasjonsnummer
//import org.junit.jupiter.api.Assertions.assertEquals
//import org.junit.jupiter.api.BeforeEach
//import org.junit.jupiter.api.Test
//import java.time.LocalDateTime
//
//private const val SOME_RINA_SAK_ID = "123456"
//    private const val SOME_RINA_SAK_ID_2 = "456987"
//    private const val SOME_DOKUMENT_ID = "1234"
//    private const val SOME_RINADOKUMENT_VERSION = "4.2"
//
//    private const val FNR = "11067122781"
//    private const val SOME_FNR = "1234567799"
//    private const val SVENSK_UID = "195420-2012"
//    private const val SVENSK_UID_UTEN_SEPERATOR = "1954202012"
//
//    private const val ITALIA = "IT"
//    private const val POLEN = "PL"
//    private const val SVERIGE = "SE"
//    private const val PENSJON_SEKTOR_KODE = "P"
//    private const val NORGE = "NO"
//
//internal class IdentOppdateringTest {
//
//    var euxService: EuxService = mockk(relaxed = true)
//    var kodeverkClient: KodeverkClient = mockk(relaxed = true)
//    var pdlFiltrering: PdlFiltrering = PdlFiltrering(kodeverkClient)
//    var oppgaveHandler: OppgaveHandler = mockk()
//    var personidentifiseringService: PersonidentifiseringService = mockk()
//    var utenlandskPersonIdentifisering = UtenlandskPersonIdentifisering()
//    lateinit var identoppdatering : IdentOppdatering
//
//    @BeforeEach
//    fun setup() {
//        every { kodeverkClient.finnLandkode("SWE") } returns SVERIGE
//        every { kodeverkClient.finnLandkode(SVERIGE) } returns "SWE"
//        every { kodeverkClient.finnLandkode(POLEN) } returns "POL"
//        every { kodeverkClient.finnLandkode("POL") } returns POLEN
//        identoppdatering = IdentOppdatering(
//            euxService,
//            pdlFiltrering,
//            oppgaveHandler,
//            kodeverkClient,
//            personidentifiseringService,
//            utenlandskPersonIdentifisering
//        )
//
//    }
//
//    @Test
//    fun `Gitt at ingen identifiserte personer blir funnet saa gjoeres ingen oppdatrering`() {
//        every { personidentifiseringService.hentIdentifisertePersoner(emptyList(), BucType.P_BUC_01) } returns emptyList()
//
//        assertEquals(
//            NoUpdate("Ingen identifiserte FNR funnet"),
//            identoppdatering.oppdaterUtenlandskIdent(sedHendelse(avsenderLand = NORGE))
//        )
//    }
//
//    @Test
//    fun `Gitt at det er en buc med flere enn en identifisert person saa blir det ingen oppdatering`() {
//        val ident = identifisertPerson()
//
//        every { personidentifiseringService.hentIdentifisertePersoner(any(), any()) } returns listOf(ident, ident.copy(fnr = fra(SOME_FNR)))
//
//        assertEquals(
//            NoUpdate("Antall identifiserte FNR er fler enn en"),
//            identoppdatering.oppdaterUtenlandskIdent(sedHendelse(avsenderLand = NORGE))
//        )
//    }
//
//    @Test
//    fun `Gitt at det er en buc uten utenlandsk ident saa blir det ingen oppdatering`() {
//        every { personidentifiseringService.hentIdentifisertePersoner(any(), any()) } returns listOf(identifisertPerson())
//
//        assertEquals(
//            NoUpdate("Ingen utenlandske IDer funnet i BUC"),
//            identoppdatering.oppdaterUtenlandskIdent(sedHendelse(avsenderLand = NORGE))
//        )
//    }
//
//    @Test
//    fun `Gitt at det er en buc med flere enn en utenlandsk ident saa blir det ingen oppdatering`() {
//        val polskPerson = forenkletSED(SOME_RINA_SAK_ID)
//        val italienskPerson = forenkletSED(SOME_RINA_SAK_ID_2)
//
//        every { personidentifiseringService.hentIdentifisertePersoner(any(), any()) } returns listOf(identifisertPerson())
//
//        every { euxService.alleGyldigeSEDForBuc(SOME_RINA_SAK_ID) } returns listOf(
//            Pair(polskPerson, sed(land = POLEN)),
//            Pair(italienskPerson, sed(FNR, land = ITALIA))
//        )
//
//        assertEquals(
//            NoUpdate("Antall utenlandske IDer er flere enn en"),
//            identoppdatering.oppdaterUtenlandskIdent(sedHendelse(avsenderLand = NORGE))
//        )
//    }
//
//    @Test
//    fun `Gitt BUCen mangler avsenderland da blir det ingen oppdatering`() {
//        val polskPerson = forenkletSED(SOME_RINA_SAK_ID)
//        every { personidentifiseringService.hentIdentifisertePersoner(any(), any()) } returns listOf(identifisertPerson())
//
//        every { euxService.alleGyldigeSEDForBuc(SOME_RINA_SAK_ID) } returns listOf(Pair(polskPerson, sed(land = "")))
//
//        assertEquals(
//            NoUpdate("Avsenderland mangler eller avsenderland er ikke det samme som uidland"),
//            identoppdatering.oppdaterUtenlandskIdent(sedHendelse(avsenderLand = NORGE))
//        )
//    }
//
//    @Test
//    fun `Gitt at avsenderland ikke er det samme som UIDland da blir det ingen oppdatering`() {
//        val polskPerson = forenkletSED(SOME_RINA_SAK_ID)
//
//        every { personidentifiseringService.hentIdentifisertePersoner(any(), any()) } returns listOf(identifisertPerson())
//        every { euxService.alleGyldigeSEDForBuc(SOME_RINA_SAK_ID) } returns listOf(Pair(polskPerson, sed(land = POLEN)))
//
//        assertEquals(
//            NoUpdate("Avsenderland mangler eller avsenderland er ikke det samme som uidland"),
//            identoppdatering.oppdaterUtenlandskIdent(sedHendelse(avsenderLand = ITALIA))
//        )
//    }
//
//    @Test
//    fun `Gitt at BUCen inneholder uid paa en doed person saa blir det ingen oppdatering`() {
//        val polskPerson = forenkletSED(SOME_RINA_SAK_ID)
//
//        every { personidentifiseringService.hentIdentifisertePersoner(any(), any()) } returns listOf(identifisertPerson(true))
//        every { euxService.alleGyldigeSEDForBuc(SOME_RINA_SAK_ID) } returns listOf(Pair(polskPerson, sed(land = POLEN)))
//
//        assertEquals(
//            NoUpdate("Identifisert person registrert med doedsfall"),
//            identoppdatering.oppdaterUtenlandskIdent(sedHendelse(avsenderLand = POLEN))
//        )
//    }
//
//    @Test
//    fun `Gitt at SEDen inneholder uid som er lik uid i PDL saa blir det ingen oppdatering`() {
//
//        val polskPerson = forenkletSED(SOME_RINA_SAK_ID)
//
//        every { personidentifiseringService.hentIdentifisertePersoner(any(), any()) } returns
//                listOf(identifisertPerson(uidFraPdl = listOf(utenlandskIdentifikasjonsnummer(FNR))))
//
//        every { euxService.alleGyldigeSEDForBuc(SOME_RINA_SAK_ID) } returns listOf(Pair(polskPerson, sed(id = FNR, land = POLEN)))
//
//        assertEquals(
//            NoUpdate("PDLuid er identisk med SEDuid"),
//            identoppdatering.oppdaterUtenlandskIdent(sedHendelse(avsenderLand = POLEN)) as NoUpdate
//        )
//    }
//
//    @Test
//    fun `Gitt at SEDen inneholder en uid som ikke er gyldig saa blir det ingen oppdatering`() {
//
//        val polskPerson = forenkletSED(SOME_RINA_SAK_ID)
//
//        every { personidentifiseringService.hentIdentifisertePersoner(any(), any()) } returns
//                listOf(identifisertPerson(uidFraPdl = listOf(utenlandskIdentifikasjonsnummer(FNR))))
//
//        every { euxService.alleGyldigeSEDForBuc(SOME_RINA_SAK_ID) } returns listOf(Pair(polskPerson, sed(land = POLEN)))
//
//        assertEquals(
//            NoUpdate("Ingen validerte identifiserte personer funnet"),
//            identoppdatering.oppdaterUtenlandskIdent(sedHendelse(avsenderLand = POLEN))
//        )
//
//    }
//
//    @Test
//    fun `Gitt at SEDen inneholder en UID som ikke finnes i PDL men det finnes allerede en annen UID i PDL saa skal det opprettes en oppgave for UID`() {
//
//        val polskPersonMedUID = forenkletSED(SOME_RINA_SAK_ID)
//        val identifisertPerson= identifisertPerson(uidFraPdl = listOf(utenlandskIdentifikasjonsnummer(SOME_FNR)))
//
//        every { personidentifiseringService.hentIdentifisertePersoner(any(), any()) } returns listOf(identifisertPerson)
//        every { euxService.alleGyldigeSEDForBuc(SOME_RINA_SAK_ID) } returns listOf(Pair(polskPersonMedUID, sed(id = FNR, land = POLEN)))
//        every { oppgaveHandler.opprettOppgaveForUid(any(), UtenlandskId( FNR, POLEN ), identifisertPerson) } returns true
//
//        assertEquals(
//            NoUpdate("Det finnes allerede en annen uid fra samme land (Oppgave)"),
//            identoppdatering.oppdaterUtenlandskIdent(sedHendelse(avsenderLand = POLEN))
//        )
//
//    }
//
//    @Test
//    fun `Gitt at vi har en endringsmelding med en svensk uid, med riktig format saa skal det opprettes en endringsmelding`() {
//        val svenskPersonMedUID = forenkletSED(SOME_RINA_SAK_ID)
//        val identifisertPerson= identifisertPerson(uidFraPdl = listOf(utenlandskIdentifikasjonsnummer(SVENSK_UID)))
//
//        every { personidentifiseringService.hentIdentifisertePersoner(any(), any()) } returns listOf(identifisertPerson)
//        every { euxService.alleGyldigeSEDForBuc(SOME_RINA_SAK_ID) } returns listOf(Pair(svenskPersonMedUID, sed(id = SVENSK_UID, land = SVERIGE)))
//        every { oppgaveHandler.opprettOppgaveForUid(any(), UtenlandskId( SVENSK_UID, SVERIGE ), identifisertPerson) } returns false
//
//        val resultat =  identoppdatering.oppdaterUtenlandskIdent(sedHendelse(avsenderLand = SVERIGE)) as Update
//        assertEquals(resultat.description, "Innsending av endringsmelding")
//
//    }
//    @Test
//    fun `Gitt at vi har en endringsmelding med en svensk uid, med feil format saa skal den konverteres foer innsending`() {
//        val svenskPersonMedUID = forenkletSED(SOME_RINA_SAK_ID)
//        val identifisertPerson= identifisertPerson(uidFraPdl = listOf(utenlandskIdentifikasjonsnummer(SVENSK_UID_UTEN_SEPERATOR)))
//
//        every { personidentifiseringService.hentIdentifisertePersoner(any(), any()) } returns listOf(identifisertPerson)
//        every { euxService.alleGyldigeSEDForBuc(SOME_RINA_SAK_ID) } returns listOf(Pair(svenskPersonMedUID, sed(id = SVENSK_UID_UTEN_SEPERATOR, land = SVERIGE)))
//        every { oppgaveHandler.opprettOppgaveForUid(any(), UtenlandskId( SVENSK_UID_UTEN_SEPERATOR, SVERIGE ), identifisertPerson) } returns false
//
//        val resultat = identoppdatering.oppdaterUtenlandskIdent(sedHendelse(avsenderLand = SVERIGE)) as Update
//        assertEquals( "Innsending av endringsmelding", resultat.description)
//
//    }
//
//    @Test
//    fun `Gitt at vi har en SedHendelse som mangler bucType saa skal vi faa en NoUpdate som resultat`() {
//        assertEquals(
//            NoUpdate("Ikke relevant for eessipensjon"),
//            identoppdatering.oppdaterUtenlandskIdent(
//                SedHendelse(
//                    bucType = null,
//                    sektorKode = "",
//                    rinaDokumentVersjon = "",
//                    rinaDokumentId = "",
//                    rinaSakId = ""
//                )
//            )
//        )
//    }
//
//    @Test
//    fun `Gitt at vi har en SedHendelse som mangler avsenderNavn saa skal vi faa en NoUpdate som resultat`() {
//
//        val svenskPersonMedUID = forenkletSED(SOME_RINA_SAK_ID)
//        val identifisertPerson= identifisertPerson(uidFraPdl = listOf(utenlandskIdentifikasjonsnummer(SVENSK_UID_UTEN_SEPERATOR)))
//
//        every { personidentifiseringService.hentIdentifisertePersoner(any(), any()) } returns listOf(identifisertPerson)
//        every { euxService.alleGyldigeSEDForBuc(SOME_RINA_SAK_ID) } returns listOf(Pair(svenskPersonMedUID, sed(id = SVENSK_UID_UTEN_SEPERATOR, land = SVERIGE)))
//        every { oppgaveHandler.opprettOppgaveForUid(any(), UtenlandskId( SVENSK_UID_UTEN_SEPERATOR, SVERIGE ), identifisertPerson) } returns false
//
//        assertEquals(
//            NoUpdate("AvsenderNavn er ikke satt, kan derfor ikke lage endringsmelding"),
//            identoppdatering.oppdaterUtenlandskIdent(sedHendelse(avsenderLand = SVERIGE, avsenderNavn = null))
//        )
//    }
//
//
//    private fun utenlandskIdentifikasjonsnummer(fnr: String) = UtenlandskIdentifikasjonsnummer(
//        identifikasjonsnummer = fnr,
//        utstederland = "POL",
//        opphoert = false,
//        folkeregistermetadata = Folkeregistermetadata(
//            gyldighetstidspunkt = LocalDateTime.now(),
//            ajourholdstidspunkt = LocalDateTime.now(),
//        ),
//        metadata = metadata()
//    )
//
//    private fun metadata() = Metadata(
//        endringer = emptyList(),
//        historisk = false,
//        master = "PDL",
//        opplysningsId = "opplysningsId"
//    )
//
//
//    private fun forenkletSED(rinaId: String) = ForenkletSED(
//        rinaId,
//        SedType.P2000,
//        SedStatus.RECEIVED
//    )
//
//
//    private fun identifisertPerson(
//        erDoed: Boolean = false, uidFraPdl: List<UtenlandskIdentifikasjonsnummer> = emptyList()
//    ) = IdentifisertPerson(
//        fnr = fra(FNR),
//        uidFraPdl = uidFraPdl,
//        aktoerId = "123456789351",
//        landkode = null,
//        geografiskTilknytning = null,
//        harAdressebeskyttelse = erDoed,
//        personListe = null,
//        personRelasjon = SEDPersonRelasjon(
//            relasjon = Relasjon.ANNET,
//            fnr = fra(FNR),
//            rinaDocumentId = SOME_RINA_SAK_ID
//        ),
//        erDoed = erDoed,
//        kontaktAdresse = null,
//    )
//
//    private fun sedHendelse(
//        bucType: BucType = BucType.P_BUC_01,
//        sedType: SedType = SedType.P2000,
//        avsenderLand: String = "SE",
//        avsenderNavn: String? = "Sverige"
//    ) = SedHendelse(
//        sektorKode = PENSJON_SEKTOR_KODE,
//        avsenderLand = avsenderLand,
//        bucType = bucType,
//        rinaSakId = SOME_RINA_SAK_ID,
//        rinaDokumentId = SOME_DOKUMENT_ID,
//        rinaDokumentVersjon = SOME_RINADOKUMENT_VERSION,
//        sedType = sedType,
//        avsenderNavn = avsenderNavn
//    )
//
//    private fun sed(id: String = SOME_FNR, brukersAdresse: Adresse? = null, land: String) = SED(
//        type = SedType.P2000,
//        sedGVer = null,
//        sedVer = null,
//        nav = Nav(
//            eessisak = null,
//            bruker = Bruker(
//                mor = null,
//                far = null,
//                person = Person(
//                    pin = listOf(
//                        PinItem(
//                            identifikator = id,
//                            land = land
//                        )
//                    ),
//                    pinland = null,
//                    statsborgerskap = null,
//                    etternavn = null,
//                    etternavnvedfoedsel = null,
//                    fornavn = null,
//                    fornavnvedfoedsel = null,
//                    tidligerefornavn = null,
//                    tidligereetternavn = null,
//                    kjoenn = null,
//                    foedested = null,
//                    foedselsdato = null,
//                    sivilstand = null,
//                    relasjontilavdod = null,
//                    rolle = null
//                ),
//                adresse = brukersAdresse,
//                arbeidsforhold = null,
//                bank = null
//            )
//        ),
//        pensjon = Pensjon()
//    )
//
//
//}*/
