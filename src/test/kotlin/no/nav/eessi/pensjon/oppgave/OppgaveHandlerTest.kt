package no.nav.eessi.pensjon.oppgave

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.lagring.LagringsService
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingService
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPersonPDL
import no.nav.eessi.pensjon.personoppslag.pdl.model.Relasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.SEDPersonRelasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskIdentifikasjonsnummer
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.springframework.kafka.core.KafkaTemplate

private const val FNR = "11067122781"
internal class OppgaveHandlerTest{

    private val kafkaTemplate  = mockk<KafkaTemplate<String, String>>()
    private val lagringsService = mockk<LagringsService>()
    private val oppgaveRoutingService = mockk<OppgaveRoutingService>()
    lateinit var oppgaveHandler: OppgaveHandler

    @BeforeEach
    fun setup() {
        every { lagringsService.kanHendelsenOpprettes(any()) } returns true
        every { kafkaTemplate.defaultTopic } returns "someTopic"
        every { oppgaveRoutingService.route(any()) } returns Enhet.AUTOMATISK_JOURNALFORING

        justRun { lagringsService.lagreHendelseMedSakId(any()) }

        oppgaveHandler = OppgaveHandler(kafkaTemplate, lagringsService, oppgaveRoutingService, "Q2")
        oppgaveHandler.initMetrics()
    }

    @Test
    fun `gitt en sedhendelse og en identifisert person, så skal det opprettes en oppgavemelding`() {
        val identifisertPerson = identifisertPerson()
        val oppgave = OppgaveData(enSedHendelse(), identifisertPerson)

        val meldingSlot = slot<String>()
        every { kafkaTemplate.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        oppgaveHandler.opprettOppgaveForUid(oppgave)

        val forventetMelding = """{
          "sedType" : null,
          "journalpostId" : null,
          "tildeltEnhetsnr" : "9999",
          "aktoerId" : "123456789351",
          "rinaSakId" : "74389487",
          "hendelseType" : "MOTTATT",
          "filnavn" : null,
          "oppgaveType" : "PDL"
        }""".trimIndent()

        JSONAssert.assertEquals(forventetMelding, meldingSlot.captured, false)

    }
    fun enSedHendelse() = SedHendelse(
        sektorKode = "P",
        bucType = BucType.P_BUC_01,
        sedType = SedType.P2100,
        rinaSakId = "74389487",
        rinaDokumentId = "743982",
        rinaDokumentVersjon = "1",
        avsenderNavn = "Svensk institusjon",
        avsenderLand = "SE"
    )

    private fun identifisertPerson(
        erDoed: Boolean = false, uidFraPdl: List<UtenlandskIdentifikasjonsnummer> = emptyList()
    ) = IdentifisertPersonPDL(
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
}