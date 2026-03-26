package no.nav.eessi.pensjon.pdl.dodsmelding

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.klienter.saf.BrukerIdType
import no.nav.eessi.pensjon.klienter.saf.SafClient
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.Ident
import no.nav.person.pdl.leesah.Personhendelse
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.kafka.support.Acknowledgment

class MeldingFraPdlListenerTest {

    private val mockAck = mockk<Acknowledgment>()
    private val mapper = configureObjectMapper()
    private val safClient = mockk<SafClient>(relaxed = true)
    private val personService = mockk<PersonService>()
    private val ack = mockk<Acknowledgment>()

    private lateinit var listener : MeldingFraPdlListener
    private lateinit var dodsmeldingBehandler : DodsmeldingBehandler

    private lateinit var personhendelse: Personhendelse
    @BeforeEach
    fun setup() {
        dodsmeldingBehandler = DodsmeldingBehandler(safClient, personService)
        listener = MeldingFraPdlListener(dodsmeldingBehandler)
        justRun { ack.acknowledge() }

        personhendelse = mockk<Personhendelse> {
            every { opplysningstype } returns "DOEDSFALL_V1"
            every { personidenter } returns listOf("12345678901")
            every { hendelseId } returns "HendelseFraPDL"
        }
    }

    @Test
    fun `personalhendelse på sivilstand skal gå ok`() {
        val hendelse = hentHendelsefraFil("/leesha/leesha_sivilstandhendelse1.json")

        listener.mottaLeesahMelding(mockConsumerRecord(hendelse), mockAck)
    }

    @Test
    fun `personhendelse på dødsfall records skal gå ok`() {
        val hendelse1 = hentHendelsefraFil("/leesha/leesha_doedsfall_hendelse1.json")

        val ident = Ident.bestemIdent("1000016953359")
        every { personService.hentPerson(ident) } returns null

        listener.mottaLeesahMelding(mockConsumerRecord(hendelse1), mockAck)

        verify(exactly = 0 ) { mockAck.acknowledge() }
    }

    @Test
    fun `mottaLeesahMelding på dødsfall med gyldig utenlandsk ident henter dokumentmetadata`() {

        val ident = Ident.bestemIdent("12345678901")
        every { personService.hentPerson(ident) } returns mockk {
            every { utenlandskIdentifikasjonsnummer } returns listOf(
                mockk { every { utstederland } returns "SWE" }
            )
        }

        every { safClient.hentDokumentMetadata("12345678901", BrukerIdType.FNR) } returns mockk {
            every { data } returns mockk {
                every { dokumentoversiktBruker } returns mockk {
                    every { journalposter } returns emptyList()
                }
            }
        }

        listener.mottaLeesahMelding(
            ConsumerRecord("topic", 0, 1L, personhendelse.hendelseId, personhendelse),
            ack
        )

        verify(exactly = 1) { safClient.hentDokumentMetadata("12345678901", BrukerIdType.FNR) }
    }

    @Test
    fun `mottaLeesahMelding på dødsfall uten gyldig ident logger melding og kaller ikke saf`() {
        val ident = Ident.bestemIdent("12345678901")
        every { personService.hentPerson(ident) } returns mockk { every { utenlandskIdentifikasjonsnummer } returns emptyList() }

        listener.mottaLeesahMelding(ConsumerRecord("topic", 0, 1L, personhendelse.hendelseId, personhendelse), ack)

        verify(exactly = 1) { personService.hentPerson(any()) }
        verify(exactly = 0) { safClient.hentDokumentMetadata(any(), any()) }
    }

    private fun mockConsumerRecord(personhendelse: Personhendelse): ConsumerRecord<String, Personhendelse> =
            ConsumerRecord("topic", 0, 1L, personhendelse.hendelseId, personhendelse)


    private fun hentHendelsefraFil(hendelseJson: String): Personhendelse =
        mapper.readValue(javaClass.getResource(hendelseJson).readText(), Personhendelse::class.java)

    private fun hentHendelsefraFil(hendelseJson: String, oldpid: String, newpid: String): Personhendelse =
        mapper.readValue(javaClass.getResource(hendelseJson).readText().replace(oldpid, newpid), Personhendelse::class.java)


    fun configureObjectMapper(): ObjectMapper {
        return JsonMapper.builder()
            .addModule(JavaTimeModule())
            .configure(MapperFeature.USE_ANNOTATIONS, false)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build()
    }
}