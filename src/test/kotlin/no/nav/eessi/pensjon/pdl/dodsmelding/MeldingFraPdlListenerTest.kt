package no.nav.eessi.pensjon.pdl.dodsmelding

import ch.qos.logback.classic.spi.ILoggingEvent
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.eessi.pensjon.klienter.saf.BrukerIdType
import no.nav.eessi.pensjon.klienter.saf.SafClient
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.Ident
import no.nav.eessi.pensjon.OpprettH070.OpprettH070
import no.nav.eessi.pensjon.personoppslag.pdl.model.Doedsfall
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endring
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endringstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Foedselsdato
import no.nav.eessi.pensjon.personoppslag.pdl.model.Folkeregistermetadata
import no.nav.eessi.pensjon.personoppslag.pdl.model.ForelderBarnRelasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.Kjoenn
import no.nav.eessi.pensjon.personoppslag.pdl.model.KjoennType
import no.nav.eessi.pensjon.personoppslag.pdl.model.Metadata as PDLMetaData
import no.nav.eessi.pensjon.personoppslag.pdl.model.Navn
import no.nav.eessi.pensjon.personoppslag.pdl.model.PdlPerson
import no.nav.eessi.pensjon.personoppslag.pdl.model.Sivilstand
import no.nav.eessi.pensjon.personoppslag.pdl.model.Statsborgerskap
import no.nav.person.pdl.leesah.Personhendelse
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.kafka.support.Acknowledgment
import java.time.LocalDate
import java.time.LocalDateTime

class MeldingFraPdlListenerTest {

    private val mockAck = mockk<Acknowledgment>()
    private val mapper = configureObjectMapper()
    private val safClient = mockk<SafClient>(relaxed = true)
    private val personService = mockk<PersonService>()
    private val ack = mockk<Acknowledgment>()
    private val opprettH070 = OpprettH070()

    private lateinit var listener : MeldingFraPdlListener
    private lateinit var dodsmeldingBehandler : DodsmeldingBehandler

    private lateinit var personhendelse: Personhendelse
    @BeforeEach
    fun setup() {
        dodsmeldingBehandler = DodsmeldingBehandler(safClient, personService, opprettH070)
        listener = MeldingFraPdlListener(dodsmeldingBehandler)
        justRun { ack.acknowledge() }

        personhendelse = mockk<Personhendelse> {
            every { opplysningstype } returns "DOEDSFALL_V1"
            every { personidenter } returns listOf("12345678901")
            every { hendelseId } returns "HendelseFraPDL"
            every { doedsfall } returns no.nav.person.pdl.leesah.doedsfall.Doedsfall.newBuilder().setDoedsdato(LocalDate.now()).build()
        }
        var argumentCaptor = slot<ILoggingEvent>()

    }

    @Test
    fun `personalhendelse på sivilstand skal gå ok`() {
        val hendelse = hentHendelsefraFil("/leesha/leesha_sivilstandhendelse1.json")

        listener.mottaLeesahMelding(mockConsumerRecord(listOf(hendelse)), mockAck)
    }

    @Test
    fun `personhendelse på dødsfall records skal gå ok`() {
        val hendelse1 = hentHendelsefraFil("/leesha/leesha_doedsfall_hendelse1.json")

        val ident = Ident.bestemIdent("1000016953359")
        every { personService.hentPerson(ident) } returns null

        listener.mottaLeesahMelding(mockConsumerRecord(listOf(hendelse1)), mockAck)

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
            listOf(ConsumerRecord("topic", 0, 1L, personhendelse.hendelseId, personhendelse)),
            ack
        )

        verify(exactly = 1) { safClient.hentDokumentMetadata("12345678901", BrukerIdType.FNR) }
    }

    @Test
    fun `mottaLeesahMelding på dødsfall med gyldig utenlandsk ident fra Sverige henter dokumentmetadata og oppretter H070`() {
        val ident = Ident.bestemIdent("12345678901")

        every { personService.hentPerson(ident) } returns mockk(relaxed = true) {
            every { utenlandskIdentifikasjonsnummer } returns listOf(
                mockk { every { utstederland } returns "SWE"
                        every { identifikasjonsnummer } returns "12345678901"
                }
            )
            every { identer } returns listOf(IdentInformasjon("12345678903", IdentGruppe.FOLKEREGISTERIDENT))
            every { navn } returns Navn(fornavn = "Karen", etternavn = "Nordmann", metadata = mockMeta())
            every { foedselsdato } returns Foedselsdato(foedselsdato = "1999-10-10", metadata = mockMeta())
            every { kjoenn } returns Kjoenn(KjoennType.KVINNE, metadata = mockMeta())
            every { doedsfall } returns Doedsfall(doedsdato = LocalDate.now(), metadata = mockMeta())
        }

        every { safClient.hentDokumentMetadata(any(), BrukerIdType.FNR) } returns mockk {
            every { data } returns mockk {
                every { dokumentoversiktBruker } returns mockk {
                    every { journalposter } returns listOf(
                        mockk {
                            every { journalpostId } returns "123"
                            every { datoOpprettet } returns null
                            every { tittel } returns "Test Journalpost"
                            every { tilleggsopplysninger } returns listOf(
                                mapOf("nokkel" to "eessi_pensjon_bucid", "verdi" to "buc-123")
                            )
                            every { dokumenter } returns listOf(
                                mockk {
                                    every { dokumentInfoId } returns "456"
                                }
                            )
                        }
                    )
                }
            }
        }

        listener.mottaLeesahMelding(
            listOf(ConsumerRecord("topic", 0, 1L, personhendelse.hendelseId, personhendelse)),
            ack
        )

        verify(exactly = 1) { safClient.hentDokumentMetadata("12345678901", BrukerIdType.FNR) }
        //verify(exactly = 1) { opprettH070.oppretterH070(personhendelse, pdlPerson) }

        //assertEquals("""""", pdlPerson)
    }

    @Test
    fun `mottaLeesahMelding på dødsfall uten gyldig ident logger melding og kaller ikke saf`() {
        val ident = Ident.bestemIdent("12345678901")
        every { personService.hentPerson(ident) } returns mockk { every { utenlandskIdentifikasjonsnummer } returns emptyList() }

        listener.mottaLeesahMelding(listOf(ConsumerRecord("topic", 0, 1L, personhendelse.hendelseId, personhendelse)), ack)

        verify(exactly = 1) { personService.hentPerson(any()) }
        verify(exactly = 0) { safClient.hentDokumentMetadata(any(), any()) }
    }

    private fun mockConsumerRecord(personhendelse: List<Personhendelse>): List<ConsumerRecord<String, Personhendelse>> =
        personhendelse.map {
            ConsumerRecord("topic", 0, 1L, it.hendelseId, it)
        }

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

    fun lagPerson(
        fnr: String = "12345678901" ,
        fornavn: String = "Fornavn",
        etternavn: String = "Etternavn",
        familierlasjon: List<ForelderBarnRelasjon> = emptyList(),
        sivilstand: List<Sivilstand> = emptyList()
    ) = PdlPerson(
        listOf(IdentInformasjon(fnr, IdentGruppe.AKTORID)),
        Navn(fornavn, null,  etternavn, null, null, null, mockMeta()),
        emptyList(),
        null,
        null,
        listOf(
            Statsborgerskap(
                "NOR",
                LocalDate.of(2010, 10, 11),
                LocalDate.of(2020, 10, 2),
                mockMeta()
            )
        ),
        null,
        null,
        null,
        Kjoenn(
            KjoennType.MANN,
            Folkeregistermetadata(LocalDateTime.of(2000, 10, 1, 12, 10, 31)),
            mockMeta()
        ),
        null,
        familierlasjon,
        sivilstand,
        null,
        null,
        emptyList()
    )

    private fun mockMeta() : PDLMetaData {
        return PDLMetaData(
            listOf(Endring(
                "DOLLY",
                LocalDateTime.of(2010, 4, 1, 10, 12, 3),
                "Dolly",
                "FREG",
                Endringstype.OPPRETT
            )),
            false,
            "FREG",
            "fdsa234-sdfsf234-sfsdf234"
        )
    }

}