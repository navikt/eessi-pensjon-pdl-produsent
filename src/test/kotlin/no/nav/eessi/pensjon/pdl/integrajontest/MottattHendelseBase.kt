package no.nav.eessi.pensjon.pdl.integrajontest

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.buc.EuxDokumentHelper
import no.nav.eessi.pensjon.buc.EuxKlient
import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.EessisakItem
import no.nav.eessi.pensjon.eux.model.sed.Krav
import no.nav.eessi.pensjon.eux.model.sed.KravType
import no.nav.eessi.pensjon.eux.model.sed.Nav
import no.nav.eessi.pensjon.eux.model.sed.Pensjon
import no.nav.eessi.pensjon.eux.model.sed.Person
import no.nav.eessi.pensjon.eux.model.sed.PinItem
import no.nav.eessi.pensjon.eux.model.sed.RelasjonAvdodItem
import no.nav.eessi.pensjon.eux.model.sed.RelasjonTilAvdod
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.eux.model.sed.SivilstandItem
import no.nav.eessi.pensjon.eux.model.sed.StatsborgerskapItem
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.klienter.norg2.Norg2Service
import no.nav.eessi.pensjon.listeners.SedMottattListener
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import no.nav.eessi.pensjon.personidentifisering.helpers.Rolle
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
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskAdresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.Vegadresse
import no.nav.eessi.pensjon.sed.SedHendelseModel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.time.LocalDateTime

internal open class MottattHendelseBase {

    private val euxKlient: EuxKlient = mockk()
    private val dokumentHelper = EuxDokumentHelper(euxKlient)
    protected val norg2Service: Norg2Service = mockk(relaxed = true)

    protected val personService: PersonService = mockk(relaxed = true)
    private val personidentifiseringService = PersonidentifiseringService(personService)

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
        personidentifiseringService = personidentifiseringService,
        dokumentHelper = dokumentHelper,
        profile = "test"
    )

    @BeforeEach
    fun setup() {
        mottattListener.initMetrics()
        dokumentHelper.initMetrics()
    }

    @AfterEach
    fun after() {
        clearAllMocks()
    }

    protected fun testRunner(
        fnr: String?,
        uid: String?,
        hendelse: SedHendelseModel,
        sed: SED,
        assertBlock: (IdentifisertPerson) -> Unit
    ) {
        initCommonMocks(sed)

        if (fnr != null) {
            every { personService.hentPerson(NorskIdent(fnr)) } returns createBrukerWith(
                fnr,
                "Mamma forsørger",
                "Etternavn",
                hendelse.avsenderLand,
                aktorId = AKTOER_ID
            )
        }

        mottattListener.consumeSedMottatt(hendelse.toJson(), mockk(relaxed = true), mockk(relaxed = true))

        verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }

        clearAllMocks()
    }

    fun initCommonMocks(sed: SED) {
        every { euxKlient.hentSedJson(any(), any()) } returns sed.toJson()
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

    protected fun createSed(
        sedType: SedType,
        fnr: String? = null,
        annenPerson: Person? = null,
        eessiSaknr: String? = null,
        fdato: String? = "1988-07-12",
        pdlPerson: no.nav.eessi.pensjon.personoppslag.pdl.model.Person? = null
    ): SED {
        val validFnr = Fodselsnummer.fra(fnr)

        val pdlForsikret = if (annenPerson == null) pdlPerson else null

        val forsikretBruker = Bruker(
            person = Person(
                pin = validFnr?.let { listOf(PinItem(identifikator = it.value, land = "NO")) },
                foedselsdato = validFnr?.getBirthDateAsIso() ?: fdato,
                fornavn = "${pdlForsikret?.navn?.fornavn}",
                etternavn = "${pdlForsikret?.navn?.etternavn}"
            )
        )

        return SED(
            sedType,
            nav = Nav(
                eessisak = eessiSaknr?.let { listOf(EessisakItem(saksnummer = eessiSaknr, land = "NO")) },
                bruker = forsikretBruker,
                annenperson = annenPerson?.let { Bruker(person = it) }
            )
        )
    }

    protected fun createSedPensjon(
        sedType: SedType,
        fnr: String?,
        eessiSaknr: String? = null,
        gjenlevendeFnr: String? = null,
        krav: KravType? = null,
        relasjon: RelasjonTilAvdod? = null,
        pdlPerson: no.nav.eessi.pensjon.personoppslag.pdl.model.Person? = null,
        sivilstand: SivilstandItem? = null,
        statsborgerskap: StatsborgerskapItem? = null,
        fdato: String? = null
    ): SED {
        val validFnr = Fodselsnummer.fra(fnr)

        val pdlPersonAnnen = if (relasjon != null) pdlPerson else null
        val pdlForsikret = if (relasjon == null) pdlPerson else null

        val foedselsdato = fdato ?: (validFnr?.getBirthDateAsIso() ?: "1988-07-12")

        val forsikretBruker = Bruker(
            person = Person(
                pin = validFnr?.let { listOf(PinItem(identifikator = it.value, land = "NO")) },
                foedselsdato = foedselsdato,
                fornavn = pdlForsikret?.navn?.fornavn,
                etternavn = pdlForsikret?.navn?.etternavn,
                sivilstand = createSivilstand(sivilstand),
                statsborgerskap = createStatsborger(statsborgerskap)
            )
        )


        val annenPerson = Bruker(person = createAnnenPerson(gjenlevendeFnr, relasjon = relasjon, pdlPerson = pdlPersonAnnen))

        val pensjon = if (gjenlevendeFnr != null || pdlPersonAnnen != null) {
            Pensjon(gjenlevende = annenPerson)
        }  else {
            null
        }

        return SED(
            sedType,
            nav = Nav(
                eessisak = eessiSaknr?.let { listOf(EessisakItem(saksnummer = eessiSaknr, land = "NO")) },
                bruker = forsikretBruker,
                krav = Krav("2019-02-01", krav?.kode)
            ),
            pensjon = pensjon
        )
    }
    private fun createSivilstand(sivilstand: SivilstandItem?): List<SivilstandItem>? = if (sivilstand != null) listOf(sivilstand) else null

    private fun createStatsborger(statsborgerskap: StatsborgerskapItem?): List<StatsborgerskapItem>? = if (statsborgerskap != null) listOf(statsborgerskap) else null

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
        fnr: String? = null,
        rolle: Rolle? = Rolle.ETTERLATTE,
        relasjon: RelasjonTilAvdod? = null,
        pdlPerson: no.nav.eessi.pensjon.personoppslag.pdl.model.Person? = null
    ): Person {
        if (fnr != null && fnr.isBlank()) {
            return Person(
                foedselsdato = "1962-07-18",
                rolle = rolle?.kode,
                relasjontilavdod = relasjon?.let { RelasjonAvdodItem(it.name) }
            )
        }
        val validFnr = Fodselsnummer.fra(fnr)

        return Person(
            validFnr?.let { listOf(PinItem(land = "NO", identifikator = it.value)) },
            foedselsdato = validFnr?.getBirthDateAsIso() ?: "1962-07-18",
            rolle = rolle?.kode,
            relasjontilavdod = relasjon?.let { RelasjonAvdodItem(it.name) },
            fornavn = "${pdlPerson?.navn?.fornavn}",
            etternavn = "${pdlPerson?.navn?.etternavn}"
        )
    }

}