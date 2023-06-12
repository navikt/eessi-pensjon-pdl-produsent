package no.nav.eessi.pensjon.pdl.adresseoppdatering

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import no.nav.eessi.pensjon.pdl.integrationtest.IntegrationBase
import no.nav.eessi.pensjon.pdl.integrationtest.KafkaTestConfig
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.retry.annotation.EnableRetry
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.HttpClientErrorException

@SpringBootTest( classes = [KafkaTestConfig::class, IntegrationBase.TestConfig::class, TestSedHendelseBehandlerRetryConfig::class, SedHendelseBehandler::class, SedListenerAdresse::class, SedHendelseBehandlerRetryLogger::class])
@ActiveProfiles("retryConfigOverride")
@DirtiesContext
@EnableRetry
@EmbeddedKafka(
    controlledShutdown = true,
    topics = ["eessi-basis-sedMottatt-v1"]
)
class SedHendelseListenerExceptionTest : IntegrationBase() {

    @MockkBean
    lateinit var personMottakKlient: PersonMottakKlient

    @MockkBean
    lateinit var adresseoppdatering: VurderAdresseoppdatering

    @Autowired
    lateinit var sedHendelseBehandler: SedHendelseBehandler

    @Autowired
    lateinit var sedListenerAdresse: SedListenerAdresse

    @BeforeEach
    fun setUp(){
        sedListenerAdresse.initMetrics()
    }

    @Test
    fun `Gitt en at vi faar 423 LOCKED fra PDL så gjoer vi ingen retry`() {

        val ack = mockk<Acknowledgment>()
        justRun { ack.acknowledge() }

        every { adresseoppdatering.vurderUtenlandskKontaktadresse(any()) } throws HttpClientErrorException(HttpStatus.LOCKED)
        sedListenerAdresse.consumeSedMottatt(enSedHendelse(), mockk(relaxed = true), ack )

        verify(exactly = 3) { adresseoppdatering.vurderUtenlandskKontaktadresse(any()) }
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
