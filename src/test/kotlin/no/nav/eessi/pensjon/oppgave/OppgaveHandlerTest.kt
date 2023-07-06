package no.nav.eessi.pensjon.oppgave

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.klienter.saf.*
import no.nav.eessi.pensjon.lagring.LagringsService
import no.nav.eessi.pensjon.oppgave.Behandlingstema.ALDERSPENSJON
import no.nav.eessi.pensjon.oppgave.Behandlingstema.BARNEP
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.Enhet.NFP_UTLAND_AALESUND
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPersonPDL
import no.nav.eessi.pensjon.personoppslag.pdl.model.Relasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.SEDPersonRelasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskIdentifikasjonsnummer
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.skyscreamer.jsonassert.JSONAssert
import org.springframework.kafka.core.KafkaTemplate

private const val FNR = "11067122781"
private const val AKTOER_ID = "123456789351"
private const val RINA_ID = "74389487"

private const val AUTOMATISK_JOURNALFORENDE_ENHET = "9999"
private const val BOSATT_NORGE = "NO"

internal class OppgaveHandlerTest{

    private val kafkaTemplate  = mockk<KafkaTemplate<String, String>>()
    private val lagringsService = mockk<LagringsService>()
    private val safClient = mockk<SafClient>()
    lateinit var oppgaveHandler: OppgaveHandler

    @BeforeEach
    fun setup() {
        every { lagringsService.kanHendelsenOpprettes(any()) } returns true
        every { kafkaTemplate.defaultTopic } returns "someTopic"

        justRun { lagringsService.lagreHendelseMedSakId(any()) }

        oppgaveHandler = OppgaveHandler(kafkaTemplate, lagringsService, mockk(), safClient)
        oppgaveHandler.initMetrics()
    }

    @Test
    fun `Gitt at vi får inn en P2100 med gjenlevende som er bosatt Norge så skal vi route gjenlevUid oppgave til 4862 NFP UTLAND AALESUND`() {
        val identifisertPerson = identifisertPerson(BOSATT_NORGE)
        val oppgaveData = OppgaveDataGjenlevUID(enSedHendelse(SedType.P2200, BucType.P_BUC_02), identifisertPerson)
        val hentMetadataResponse = HentMetadataResponse(
            data = Data(
                dokumentoversiktBruker = DokumentoversiktBruker(
                    journalposter = listOf(
                        journalpost(BARNEP, AUTOMATISK_JOURNALFORENDE_ENHET)
                    )
                )
            )
        )

        val meldingSlot = slot<String>()
        every { kafkaTemplate.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
        every { safClient.hentDokumentMetadata(any()) } returns hentMetadataResponse

        val actual = oppgaveHandler.opprettOppgave(oppgaveData)

        val melding = mapJsonToAny<OppgaveMelding>(meldingSlot.captured)
        assertEquals(NFP_UTLAND_AALESUND, melding.tildeltEnhetsnr)
        assertTrue(actual)

    }

    @ParameterizedTest
    @EnumSource(value = Enhet::class,
        names = ["UGYLDIG_ARKIV_TYPE", "OKONOMI_PENSJON", "DISKRESJONSKODE", "AUTOMATISK_JOURNALFORING"],
        mode = EnumSource.Mode.EXCLUDE
    )
    fun `Gitt at vi får inn en P2100 med gjenlevende som ikke er bosatt Norge så skal vi route gjenlevUid oppgave til 0001 PENSJON UTLAND`(enhet: Enhet) {
        val identifisertPerson = identifisertPerson(landkode = BOSATT_NORGE)
        val oppgaveData = OppgaveDataGjenlevUID(enSedHendelse(SedType.P2200, BucType.P_BUC_02), identifisertPerson)

        val hentMetadataResponse = HentMetadataResponse(
            data = Data(
                dokumentoversiktBruker = DokumentoversiktBruker(
                    journalposter = listOf(
                        journalpost(BARNEP, enhet.enhetsNr)
                    )
                )
            )
        )

        val meldingSlot = slot<String>()
        every { kafkaTemplate.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
        every { safClient.hentDokumentMetadata(any()) } returns hentMetadataResponse

        val actual = oppgaveHandler.opprettOppgave(oppgaveData)

        val melding = mapJsonToAny<OppgaveMelding>(meldingSlot.captured)
        assertEquals(enhet, melding.tildeltEnhetsnr)
        assertTrue(actual)

    }

    private fun journalpost(behandlingstema: Behandlingstema, enhet: String) = Journalpost(
        tilleggsopplysninger = listOf(mapOf(Pair("eessi_pensjon_bucid", RINA_ID))),
        journalpostId = "11111",
        datoOpprettet = "",
        tittel = "",
        journalfoerendeEnhet = enhet,
        tema = "PEN",
        dokumenter = null,
        behandlingstema = behandlingstema.name
    )

    @Test
    fun `gitt en sedhendelse og en identifisert person, så skal det opprettes en oppgavemelding`() {
        val identifisertPerson = identifisertPerson(landkode = BOSATT_NORGE)
        val oppgave = OppgaveDataUID(enSedHendelse(SedType.P2100, BucType.P_BUC_01), identifisertPerson)

        val meldingSlot = slot<String>()
        every { kafkaTemplate.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
        val mockEnhet = mockk<HentMetadataResponse>()
        val journalPost = mockk<Journalpost>()
        every { journalPost.journalfoerendeEnhet } returns "0001"
        every { journalPost.behandlingstema } returns ALDERSPENSJON.name
        every { journalPost.tilleggsopplysninger } returns listOf(mapOf(Pair("eessi_pensjon_bucid", RINA_ID)))
        every { mockEnhet.data.dokumentoversiktBruker.journalposter } returns listOf(journalPost)
        every { safClient.hentDokumentMetadata(any()) } returns mockEnhet

        oppgaveHandler.opprettOppgave(oppgave)

        val forventetMelding = """{
          "sedType" : null,
          "journalpostId" : null,
          "tildeltEnhetsnr" : "0001",
          "aktoerId" : "123456789351",
          "rinaSakId" : "74389487",
          "hendelseType" : "MOTTATT",
          "filnavn" : null,
          "oppgaveType" : "PDL"
        }""".trimIndent()

        JSONAssert.assertEquals(forventetMelding, meldingSlot.captured, false)

    }
    fun enSedHendelse(sedType: SedType, bucType: BucType) = SedHendelse(
        sektorKode = "P",
        bucType = bucType,
        sedType = sedType,
        rinaSakId = RINA_ID,
        rinaDokumentId = "743982",
        rinaDokumentVersjon = "1",
        avsenderNavn = "Svensk institusjon",
        avsenderLand = "SE"
    )

    private fun identifisertPerson(
        landkode: String, erDoed: Boolean = false, uidFraPdl: List<UtenlandskIdentifikasjonsnummer> = emptyList()
    ) = IdentifisertPersonPDL(
        fnr = Fodselsnummer.fra(FNR),
        uidFraPdl = uidFraPdl,
        aktoerId = AKTOER_ID,
        landkode = landkode,
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
}