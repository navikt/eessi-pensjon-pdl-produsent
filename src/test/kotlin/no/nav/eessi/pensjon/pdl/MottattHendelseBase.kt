package no.nav.eessi.pensjon.pdl

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.eux.EuxDokumentHelper
import no.nav.eessi.pensjon.eux.EuxKlient
import no.nav.eessi.pensjon.eux.UtenlandskPersonIdentifisering
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.Nav
import no.nav.eessi.pensjon.eux.model.sed.Person
import no.nav.eessi.pensjon.eux.model.sed.PinItem
import no.nav.eessi.pensjon.eux.model.sed.RelasjonAvdodItem
import no.nav.eessi.pensjon.eux.model.sed.RelasjonTilAvdod
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.listeners.GyldigeHendelser
import no.nav.eessi.pensjon.listeners.SedMottattListener
import no.nav.eessi.pensjon.models.SedHendelseModel
import no.nav.eessi.pensjon.pdl.filtrering.PdlFiltrering
import no.nav.eessi.pensjon.pdl.validering.PdlValidering
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import no.nav.eessi.pensjon.personidentifisering.Rolle
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.AdressebeskyttelseGradering
import no.nav.eessi.pensjon.personoppslag.pdl.model.Bostedsadresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endring
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endringstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Foedsel
import no.nav.eessi.pensjon.personoppslag.pdl.model.GeografiskTilknytning
import no.nav.eessi.pensjon.personoppslag.pdl.model.GtType
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.Kjoenn
import no.nav.eessi.pensjon.personoppslag.pdl.model.KjoennType
import no.nav.eessi.pensjon.personoppslag.pdl.model.Metadata
import no.nav.eessi.pensjon.personoppslag.pdl.model.Navn
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.PersonUtenlandskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskAdresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskIdentifikasjonsnummer
import no.nav.eessi.pensjon.personoppslag.pdl.model.Vegadresse
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

internal open class MottattHendelseBase {

    private val euxKlient: EuxKlient = mockk()
    private val dokumentHelper = EuxDokumentHelper(euxKlient)
    private val kodeverkClient: KodeverkClient = mockk()
    private val personService: PersonService = mockk(relaxed = true)
    private val personidentifiseringService = PersonidentifiseringService(personService, kodeverkClient)

    private val deugLogger: Logger = LoggerFactory.getLogger("no.nav.eessi.pensjon") as Logger
    private val listAppender = ListAppender<ILoggingEvent>()

    private val personMottakKlient: PersonMottakKlient = mockk(relaxed = true)
    private val utenlandskPersonIdentifisering = mockk<UtenlandskPersonIdentifisering>(relaxed = true)
    private val pdlFiltrering = mockk<PdlFiltrering>(relaxed = true)
    private val pdlValidering = mockk<PdlValidering>(relaxed = true)

    companion object {
        const val SAK_ID = "12345"

        const val FNR_OVER_60 = "09035225916"   // SLAPP SKILPADDE
        const val FNR_VOKSEN = "11067122781"    // KRAFTIG VEGGPRYD
        const val FNR_VOKSEN_2 = "22117320034"  // LEALAUS KAKE
        const val FNR_BARN = "12011577847"      // STERK BUSK

        const val AKTOER_ID = "0123456789000"
        const val AKTOER_ID_2 = "0009876543210"
    }

    protected val mottattListener: SedMottattListener = SedMottattListener(
        personidentifiseringService,
        dokumentHelper,
        personMottakKlient,
        utenlandskPersonIdentifisering,
        pdlFiltrering,
        pdlValidering,
        "test"
    )

    @BeforeEach
    fun setup() {
        mottattListener.initMetrics()
        dokumentHelper.initMetrics()
        listAppender.start()
        deugLogger.addAppender(listAppender)

        every { kodeverkClient.finnLandkode("SE") } returns "SWE"
        every { kodeverkClient.finnLandkode("NO") } returns "NOR"
    }

    @AfterEach
    fun after() {
        listAppender.stop()
        clearAllMocks()
    }

    protected fun testRunner(
        fnr: String?,
        hendelse: SedHendelseModel,
        sed: SED,
        buc: Buc,
        uid: List<UtenlandskIdentifikasjonsnummer> = emptyList(),
        assertBlock: (List<IdentifisertPerson>?) -> Unit
    ) {
        initCommonMocks(sed, buc)

        if (fnr != null) {
            every { personService.hentPersonUtenlandskIdent(NorskIdent(fnr)) } returns createBrukerWithUid(
                fnr,
                "Mamma forsørger",
                "Etternavn",
                hendelse.avsenderLand,
                uid
            )
        }

        mottattListener.consumeSedMottatt(hendelse.toJson(), mockk(relaxed = true), mockk(relaxed = true))
        if (GyldigeHendelser.erGyldigInnkommetSed(hendelse)) {
            verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
      //      assertBlock(mottattListener.result as List<IdentifisertPerson>?)
        } else {
            assertBlock(null)
        }
        //assertTrue(sedMottattListemerAckerMelding(), "Mangler acking av melding")
        clearAllMocks()
    }


    protected fun testRunnerMedAnnenPerson(
        fnr: String?,
        annenpersonFnr: String?,
        hendelse: SedHendelseModel,
        sed: SED,
        buc: Buc,
        assertBlock: (List<IdentifisertPerson>?) -> Unit
    ) {
        initCommonMocks(sed, buc)

        if (fnr != null) {
            every { personService.hentPersonUtenlandskIdent(NorskIdent(fnr)) } returns createBrukerWithUid(
                fnr,
                "Mamma forsørger",
                "Etternavn",
                hendelse.avsenderLand
            )
        }

        if (annenpersonFnr != null) {
            every { personService.hentPersonUtenlandskIdent(NorskIdent(annenpersonFnr)) } returns createBrukerWithUid(
                annenpersonFnr,
                "Pappa annenperson",
                "PappaEtternavn",
                hendelse.avsenderLand
            )
        }

        mottattListener.consumeSedMottatt(hendelse.toJson(), mockk(relaxed = true), mockk(relaxed = true))
        if (GyldigeHendelser.erGyldigInnkommetSed(hendelse)) {
            verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
          //  assertBlock(mottattListener.result as List<IdentifisertPerson>?)
        } else {
            assertBlock(null)
        }
        clearAllMocks()
    }


    fun initCommonMocks(sed: SED, buc: Buc) {
        every { euxKlient.hentSedJson(any(), any()) } returns sed.toJson()
        every { euxKlient.hentBuc(any()) } returns buc

    }

    protected fun createBrukerWith(
        fnr: String?,
        fornavn: String = "Fornavn",
        etternavn: String = "Etternavn",
        land: String? = "NOR",
        geo: String = "1234",
        harAdressebeskyttelse: Boolean = false,
        aktorId: String? = null
    ): no.nav.eessi.pensjon.personoppslag.pdl.model.Person {

        val foedselsdato = fnr?.let { Fodselsnummer.fra(it)?.getBirthDate() }
        val utenlandskadresse = if (land == null || land == "NOR") null else UtenlandskAdresse(landkode = land)

        val identer = listOfNotNull(
            fnr?.let { IdentInformasjon(ident = it, gruppe = IdentGruppe.FOLKEREGISTERIDENT) },
            aktorId?.let { IdentInformasjon(ident = it, gruppe = IdentGruppe.AKTORID) }
        )

        val adressebeskyttelse = if (harAdressebeskyttelse) listOf(AdressebeskyttelseGradering.STRENGT_FORTROLIG)
        else listOf(AdressebeskyttelseGradering.UGRADERT)

        val metadata = Metadata(
            listOf(
                Endring(
                    "kilde",
                    LocalDateTime.now(),
                    "ole",
                    "system1",
                    Endringstype.OPPRETT
                )
            ),
            false,
            "nav",
            "1234"
        )

        return no.nav.eessi.pensjon.personoppslag.pdl.model.Person(
            identer = identer,
            navn = Navn(
                fornavn = fornavn, etternavn = etternavn, metadata = metadata
            ),
            adressebeskyttelse = adressebeskyttelse,
            bostedsadresse = Bostedsadresse(
                gyldigFraOgMed = LocalDateTime.now(),
                gyldigTilOgMed = LocalDateTime.now(),
                vegadresse = Vegadresse("Oppoverbakken", "66", null, "1920"),
                utenlandskAdresse = utenlandskadresse,
                metadata
            ),
            oppholdsadresse = null,
            statsborgerskap = emptyList(),
            foedsel = Foedsel(foedselsdato, "NOR", "OSLO", metadata = metadata),
            geografiskTilknytning = GeografiskTilknytning(GtType.KOMMUNE, geo),
            kjoenn = Kjoenn(KjoennType.KVINNE, metadata = metadata),
            doedsfall = null,
            forelderBarnRelasjon = emptyList(),
            sivilstand = emptyList()
        )
    }

    protected fun createMetadata(): Metadata {
        return Metadata(
            listOf(
                Endring(
                    "kilde",
                    LocalDateTime.now(),
                    "ole",
                    "system1",
                    Endringstype.OPPRETT
                )
            ),
            false,
            "nav",
            "1234"
        )
    }

    protected fun createBrukerWithUid(
        fnr: String?,
        fornavn: String = "Fornavn",
        etternavn: String = "Etternavn",
        land: String? = "SWE",
        uid: List<UtenlandskIdentifikasjonsnummer> = emptyList(),
    ): PersonUtenlandskIdent {

        val identer = listOfNotNull(
            fnr?.let { IdentInformasjon(ident = it, gruppe = IdentGruppe.FOLKEREGISTERIDENT) }
        )

        val metadata = createMetadata()

        return PersonUtenlandskIdent(
            identer = identer,
            navn = Navn(
                fornavn = fornavn, etternavn = etternavn, metadata = metadata
            ),
            kjoenn = Kjoenn(KjoennType.KVINNE, metadata = metadata),
            utenlandskIdentifikasjonsnummer = uid

        )
    }

    protected fun createSed(
        sedType: SedType,
        annenPerson: Person? = null,
        pin: List<PinItem> = emptyList(),
        fdato: String = "1970-01-05"
    ): SED {
        val forsikretBruker = Bruker(
            person = Person(
                pin = pin,
                foedselsdato = fdato,
                fornavn = "Firstname",
                etternavn = "Lastname"
            )
        )

        return SED(
            sedType,
            nav = Nav(
                bruker = forsikretBruker,
                annenperson = annenPerson?.let { Bruker(person = it) }
            )
        )
    }

    protected fun createHendelseJson(
        sedType: SedType,
        bucType: BucType = BucType.P_BUC_05,
        avsenderLand: String = "SE",
    ): String {
        return """
            {
              "id": 1869,
              "sedId": "${sedType.name}_b12e06dda2c7474b9998c7139c841646_2",
              "sektorKode": "P",
              "bucType": "${bucType.name}",
              "rinaSakId": "147729",
              "avsenderId": "NO:NAVT003",
              "avsenderNavn": "NAVT003",
              "avsenderLand": "$avsenderLand",
              "mottakerId": "NO:NAVT007",
              "mottakerNavn": "NAV Test 07",
              "mottakerLand": "NO",
              "rinaDokumentId": "b12e06dda2c7474b9998c7139c841646",
              "rinaDokumentVersjon": "2",
              "sedType": "${sedType.name}",
              "navBruker": null
            }
        """.trimIndent()
    }

    protected fun createAnnenPerson(
        rolle: Rolle? = Rolle.ETTERLATTE,
        relasjon: RelasjonTilAvdod? = null,
        pin: List<PinItem> = emptyList(),
        fdato: String = "1970-04-11"
    ): Person {
        return Person(
            pin = pin,
            foedselsdato = fdato,
            rolle = rolle?.kode,
            relasjontilavdod = relasjon?.let { RelasjonAvdodItem(it.name) },
            fornavn = "Firstname Otherperson",
            etternavn = "Lastname Otherperson"
        )
    }


    fun validateSedMottattListenerLoggingMessage(keyword: String): Boolean {
        val logsList: List<ILoggingEvent> = listAppender.list
        return logsList.find { logMelding ->
            logMelding.message.contains(keyword)
        }?.message?.isNotEmpty() ?: false
    }
}