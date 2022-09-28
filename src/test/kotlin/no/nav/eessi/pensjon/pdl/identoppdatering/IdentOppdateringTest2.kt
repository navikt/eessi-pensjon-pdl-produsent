package no.nav.eessi.pensjon.pdl.identoppdatering

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.UtenlandskPersonIdentifisering
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.eux.model.sed.Adresse
import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.Nav
import no.nav.eessi.pensjon.eux.model.sed.Pensjon
import no.nav.eessi.pensjon.eux.model.sed.PinItem
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.models.SedHendelse
import no.nav.eessi.pensjon.oppgave.OppgaveHandler
import no.nav.eessi.pensjon.pdl.identoppdatering.IdentOppdatering2.NoUpdate
import no.nav.eessi.pensjon.pdl.validering.PdlValidering
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.AdressebeskyttelseGradering
import no.nav.eessi.pensjon.personoppslag.pdl.model.Doedsfall
import no.nav.eessi.pensjon.personoppslag.pdl.model.Folkeregistermetadata
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.Kontaktadresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.KontaktadresseType
import no.nav.eessi.pensjon.personoppslag.pdl.model.Metadata
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.Person
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskAdresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskIdentifikasjonsnummer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

private const val FNR = "11067122781"
private const val SOME_FNR = "1234567799"
private const val POLEN = "PL"
internal class IdentOppdateringTest2 {

    var euxService: EuxService = mockk(relaxed = true)
    var kodeverkClient: KodeverkClient = mockk(relaxed = true)
    var pdlValidering = PdlValidering(kodeverkClient)
    var oppgaveHandler: OppgaveHandler = mockk()
    var personService: PersonService = mockk()
    var utenlandskPersonIdentifisering = UtenlandskPersonIdentifisering()
    lateinit var identoppdatering : IdentOppdatering2

    @BeforeEach
    fun setup() {
        every { kodeverkClient.finnLandkode("SWE") } returns "SE"
        every { kodeverkClient.finnLandkode("SE") } returns "SWE"
        every { kodeverkClient.finnLandkode("PL") } returns "POL"
        every { kodeverkClient.finnLandkode("POL") } returns "PL"
        every { kodeverkClient.finnLandkode("FIN") } returns "FI"
        every { kodeverkClient.finnLandkode("FI") } returns "FIN"
        identoppdatering = IdentOppdatering2(
            euxService,
            pdlValidering,
            oppgaveHandler,
            kodeverkClient,
            personService,
            utenlandskPersonIdentifisering
        )

    }

    @Test
    fun `Gitt at ingen identifiserte personer blir funnet saa gjoeres ingen oppdatrering`() {

        assertEquals(
            NoUpdate("Ikke relevant for eessipensjon"),
            identoppdatering.oppdaterUtenlandskIdent(sedHendelse(avsenderLand = "NO", sedType = SedType.X001))
        )
    }

    @Test
    fun `Gitt at bruker ikke har norsk pin i SED saa resulterer det i en NoUpdate`() {
        every { euxService.hentSed(any(), any()) } returns sed(id = FNR, land = "SE")

        assertEquals(
            NoUpdate("Bruker har ikke norsk pin i SED"),
            identoppdatering.oppdaterUtenlandskIdent(sedHendelse(avsenderLand = "NO"))
        )
    }

    @Test
    fun `Gitt at det er en Sed uten utenlandsk ident saa blir det ingen oppdatering`() {
        every { euxService.hentSed(any(), any()) } returns sed(id = FNR, land = "NO")

        assertEquals(
            NoUpdate("Bruker har ikke utenlandsk ident"),
            identoppdatering.oppdaterUtenlandskIdent(sedHendelse(avsenderLand = "NO"))
        )
    }

    @Test
    fun `Gitt at BUCen mangler avsenderland da blir det ingen oppdatering`() {

        assertEquals(
            NoUpdate("Avsenderland mangler eller avsenderland er ikke det samme som uidland"),
            identoppdatering.oppdaterUtenlandskIdent(sedHendelse(avsenderLand = ""))
        )
    }

    @Test
    fun `Gitt at vi har en SedHendelse som mangler avsenderNavn saa skal vi faa en NoUpdate som resultat`() {

        assertEquals(
            NoUpdate("AvsenderNavn er ikke satt, kan derfor ikke lage endringsmelding"),
            identoppdatering.oppdaterUtenlandskIdent(sedHendelse(avsenderLand = "SE", avsenderNavn = null))
        )
    }

    @Test
    fun `Gitt at avsenderland ikke er det samme som UIDland da blir det ingen oppdatering`() {
        every { euxService.hentSed(any(), any()) } returns
                sed(id = FNR, land = "FI", pinItem =  listOf(
                    PinItem(identifikator = SOME_FNR, land = "FI"),
                    PinItem(identifikator = FNR, land = "NO"))
                )

        assertEquals(
            NoUpdate("Avsenderland mangler eller avsenderland er ikke det samme som uidland"),
            identoppdatering.oppdaterUtenlandskIdent(sedHendelse())
        )
    }

    @Test
    fun `Gitt at Seden inneholder en uid der avsenderland ikke er det samme som uidland da blir det ingen oppdatering`() {
        every { euxService.hentSed(any(), any()) } returns sed(id = FNR, land = "NO")
        every { personService.hentPerson(NorskIdent(FNR)) } returns personFraPDL(id = FNR)

        assertEquals(
            NoUpdate("Avsenderland mangler eller avsenderland er ikke det samme som uidland"),
            identoppdatering.oppdaterUtenlandskIdent(sedHendelse(avsenderLand = POLEN))
        )

    }

    @Test
    fun `Gitt at SEDen inneholder en uid som ikke validerer saa blir det ingen oppdatering`() {

        every { euxService.hentSed(any(), any()) } returns
                sed(id = FNR, land = "NO", pinItem =  listOf(
                    PinItem(identifikator = "6549876543168765", land = "FI"),
                    PinItem(identifikator = FNR, land = "NO"))
                )

        assertEquals(
            NoUpdate("Ingen validerte identifiserte personer funnet"),
            identoppdatering.oppdaterUtenlandskIdent(sedHendelse(avsenderLand = "FI"))
        )
    }

    @Test
    fun `Gitt at SEDen inneholder en uid ssom er lik UID fra PDL saa blir det ingen oppdatering`() {

        every { personService.hentPerson(NorskIdent(FNR)) } returns personFraPDL(id = FNR)
            .copy(utenlandskIdentifikasjonsnummer = listOf(utenlandskIdentifikasjonsnummer("130177-308T").copy(utstederland = "FIN")))
        every { euxService.hentSed(any(), any()) } returns
                sed(id = FNR, land = "NO", pinItem =  listOf(
                    PinItem(identifikator = "130177-308T", land = "FI"),
                    PinItem(identifikator = FNR, land = "NO"))
                )

        assertEquals(
            NoUpdate("PDL uid er identisk med SED uid"),
            identoppdatering.oppdaterUtenlandskIdent(sedHendelse(avsenderLand = "FI"))
        )
    }

    @Test
    fun `Gitt at SEDen inneholder en uid som er ulik UID fra PDL saa blir det opprettet en oppgave`() {

        every { personService.hentPerson(NorskIdent(FNR)) } returns personFraPDL(id = FNR).copy(identer = listOf(
            IdentInformasjon(FNR, IdentGruppe.FOLKEREGISTERIDENT),
            IdentInformasjon("32165498732", IdentGruppe.AKTORID)
        ))
            .copy(utenlandskIdentifikasjonsnummer = listOf(utenlandskIdentifikasjonsnummer("77011313345").copy(utstederland = "POL")))
        every { euxService.hentSed(any(), any()) } returns
                sed(id = FNR, land = "NO", pinItem =  listOf(
                    PinItem(identifikator = "77011312345", land = "PL"),
                    PinItem(identifikator = FNR, land = "NO"))
                )

        every { oppgaveHandler.opprettOppgaveForUid(any(), any(), any()) } returns true

        assertEquals(
            NoUpdate("Det finnes allerede en annen uid fra samme land (Oppgave)"),
            identoppdatering.oppdaterUtenlandskIdent(sedHendelse(avsenderLand = "PL"))
        )
    }



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


    private fun forenkletSED(rinaId: String) = ForenkletSED(
        rinaId,
        SedType.P2000,
        SedStatus.RECEIVED
    )


//    private fun identifisertPerson(
//        erDoed: Boolean = false, uidFraPdl: List<UtenlandskIdentifikasjonsnummer> = emptyList()
//    ) = IdentifisertPerson(
//        fnr = Fodselsnummer.fra(FNR),
//        uidFraPdl = uidFraPdl,
//        aktoerId = "123456789351",
//        landkode = null,
//        geografiskTilknytning = null,
//        harAdressebeskyttelse = erDoed,
//        personListe = null,
//        personRelasjon = SEDPersonRelasjon(
//            relasjon = Relasjon.ANNET,
//            fnr = Fodselsnummer.fra(FNR),
//            rinaDocumentId = SOME_RINA_SAK_ID
//        ),
//        erDoed = erDoed,
//        kontaktAdresse = null,
//    )

    private fun sedHendelse(
        bucType: BucType = BucType.P_BUC_01,
        sedType: SedType = SedType.P2000,
        avsenderLand: String = "SE",
        avsenderNavn: String? = "Sverige"
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