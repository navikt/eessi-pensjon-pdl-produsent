package no.nav.eessi.pensjon.pdl.identoppdatering

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.oppgave.OppgaveHandler
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import no.nav.eessi.pensjon.pdl.integrationtest.IntegrationBase
import no.nav.eessi.pensjon.pdl.integrationtest.KafkaTestConfig
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.retry.annotation.EnableRetry
import org.springframework.stereotype.Component
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.HttpClientErrorException


@SpringBootTest(classes = [
    KafkaTestConfig::class,
    IntegrationBase.TestConfig::class,
    SedListenerIdent::class,
    SedHendelseIdentBehandler::class,
    SedListenerTestConfig::class,
    SedHendelseIdentBehandlerRetryLogger::class
])
@ActiveProfiles("retryConfigOverride")
@DirtiesContext
@EnableRetry
@EmbeddedKafka(
    controlledShutdown = true,
    topics = ["eessi-basis-sedMottatt-v1"]
)
class SedListenerTest {

    @Autowired
    private lateinit var sedListenerIdent: SedListenerIdent

    @Autowired
    private lateinit var sedHendelseIdentBehandler: SedHendelseIdentBehandler

    @MockkBean
    private lateinit var vurderIdentoppdatering: VurderIdentoppdatering

    @MockkBean
    private lateinit var personMottakKlient: PersonMottakKlient

    @MockkBean
    private lateinit var oppgaveHandler: OppgaveHandler

    @Test
    fun `Sjekk om brukers fnr stemmer overens med brukers fdato foer vi vuderer aa oppdatere UID på bruker`() {

        println("FDATO: ${Fodselsnummer.fra("47429133544")?.getBirthDate()}")
        val ack = mockk<Acknowledgment>()
        justRun { ack.acknowledge() }

        every { vurderIdentoppdatering.vurderUtenlandskIdent(any()) } throws HttpClientErrorException(HttpStatus.LOCKED)
        sedListenerIdent.consumeSedMottatt(enSedHendelse(), mockk(relaxed = true), ack)

        verify(exactly = 3) { vurderIdentoppdatering.vurderUtenlandskIdent(any()) }
        verify(exactly = 1) { ack.acknowledge() }

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
    ).toJson()

}

@Profile("retryConfigOverride")
@Component("sedHendelseIdentBehandlerRetryConfig")
data class SedListenerTestConfig(val initialRetryMillis: Long = 10L)