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
import no.nav.eessi.pensjon.eux.model.sed.Person
import no.nav.eessi.pensjon.eux.model.sed.PinItem
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.models.SedHendelse
import no.nav.eessi.pensjon.oppgave.OppgaveHandler
import no.nav.eessi.pensjon.pdl.filtrering.PdlFiltrering
import no.nav.eessi.pensjon.pdl.identoppdatering.IdentOppdatering.NoUpdate
import no.nav.eessi.pensjon.pdl.validering.PdlValidering
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import no.nav.eessi.pensjon.personidentifisering.SEDPersonRelasjon
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

    private const val SOME_RINA_SAK_ID = "123456"
    private const val SOME_RINA_SAK_ID_2 = "456987"
    private const val SOME_DOKUMENT_ID = "1234"
    private const val SOME_FNR = "1234567799"

    private const val FNR = "11067122781"
    private const val SOME_RINADOKUMENT_VERSION = "4.2"
    private const val PENSJON_SEKTOR_KODE = "P"
    private const val ITALIA = "IT"
    private const val POLEN = "PL"

internal class IdentOppdateringTest {

    var euxService: EuxService = mockk(relaxed = true)
    var kodeverkClient: KodeverkClient = mockk(relaxed = true)
    var pdlFiltrering: PdlFiltrering = PdlFiltrering(kodeverkClient)
    var pdlValidering = PdlValidering (kodeverkClient)
    var oppgaveHandler: OppgaveHandler = mockk()
    var personidentifiseringService: PersonidentifiseringService = mockk()
    var utenlandskPersonIdentifisering = UtenlandskPersonIdentifisering()
    lateinit var identoppdatering  :IdentOppdatering

    @BeforeEach
    fun setup() {
        every { kodeverkClient.finnLandkode("SWE") } returns "SE"
        every { kodeverkClient.finnLandkode("SE") } returns "SWE"
        identoppdatering = IdentOppdatering(
            euxService,
            pdlFiltrering,
            pdlValidering,
            oppgaveHandler,
            kodeverkClient,
            personidentifiseringService,
            utenlandskPersonIdentifisering
        )

    }

    @Test
    fun `Gitt at ingen identifiserte personer blir funnet saa gjoeres ingen oppdatrering`() {

        every { personidentifiseringService.hentIdentifisertePersoner(emptyList(), BucType.P_BUC_01) } returns emptyList()

        val sedHendelse = sedHendelse()

        val resultat = identoppdatering.oppdaterUtenlandskIdent(sedHendelse) as NoUpdate
        print(resultat.description)
        assertTrue(resultat.description == "Ingen identifiserte FNR funnet, Acket melding")
    }

    @Test
    fun `Gitt at det er en buc med flere enn en identifisert person saa blir det ingen oppdatering`() {

        val ident = identifisertPerson()

        every { personidentifiseringService.hentIdentifisertePersoner(any(), any()) } returns listOf(ident, ident.copy(fnr = Fodselsnummer.fra(SOME_FNR)))

        val resultat = identoppdatering.oppdaterUtenlandskIdent(sedHendelse()) as NoUpdate
        assertTrue(resultat.description == "Antall identifiserte FNR er fler enn en, Acket melding")

    }

    @Test
    fun `Gitt at det er en buc uten utenlandsk ident saa blir det ingen oppdatering`() {

        every { personidentifiseringService.hentIdentifisertePersoner(any(), any()) } returns listOf(identifisertPerson())

        val resultat = identoppdatering.oppdaterUtenlandskIdent(sedHendelse()) as NoUpdate
        assertTrue(resultat.description == "Ingen utenlandske IDer funnet i BUC")

    }

    @Test
    fun `Gitt at det er en buc med flere enn en utenlandsk ident saa blir det ingen oppdatering`() {

        val polskPerson = forenkletSED(SOME_RINA_SAK_ID)
        val italienskPerson = forenkletSED(SOME_RINA_SAK_ID_2)

        every { personidentifiseringService.hentIdentifisertePersoner(any(), any()) } returns listOf(identifisertPerson())

        every { euxService.alleGyldigeSEDForBuc(SOME_RINA_SAK_ID) } returns listOf(
            Pair(polskPerson, sed(land = POLEN)),
            Pair(italienskPerson, sed(FNR, land = ITALIA))
        )

        val resultat = identoppdatering.oppdaterUtenlandskIdent(sedHendelse()) as NoUpdate
        assertEquals("Antall utenlandske IDer er flere enn en", resultat.description)

    }

    private fun forenkletSED(rinaId: String) = ForenkletSED(
        rinaId,
        SedType.P2000,
        SedStatus.RECEIVED
    )


    private fun identifisertPerson() = IdentifisertPerson(
        fnr = Fodselsnummer.fra(FNR),
        uidFraPdl = emptyList(),
        aktoerId = "123456789351",
        landkode = null,
        geografiskTilknytning = null,
        harAdressebeskyttelse = false,
        personListe = null,
        personRelasjon = SEDPersonRelasjon(
            relasjon = Relasjon.ANNET,
            fnr = Fodselsnummer.fra(FNR),
            rinaDocumentId = SOME_RINA_SAK_ID
        ),
        erDoed = false,
        kontaktAdresse = null,
    )

    private fun sedHendelse(
        bucType: BucType = BucType.P_BUC_01,
        sedType: SedType = SedType.P2000
    ) = SedHendelse(
        sektorKode = PENSJON_SEKTOR_KODE,
        bucType = bucType,
        rinaSakId = SOME_RINA_SAK_ID,
        rinaDokumentId = SOME_DOKUMENT_ID,
        rinaDokumentVersjon = SOME_RINADOKUMENT_VERSION,
        sedType = sedType
    )

    private fun sed(id: String = SOME_FNR, brukersAdresse: Adresse? = null, land: String) = SED(
        type = SedType.P2000,
        sedGVer = null,
        sedVer = null,
        nav = Nav(
            eessisak = null,
            bruker = Bruker(
                mor = null,
                far = null,
                person = Person(
                    pin = listOf(
                        PinItem(
                            identifikator = id,
                            land = land
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


}