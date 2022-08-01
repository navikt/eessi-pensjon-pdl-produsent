package no.nav.eessi.pensjon.pdl.adresseoppdatering

import com.ninjasquad.springmockk.MockkBean
import io.mockk.mockk
import no.nav.eessi.pensjon.listeners.SedListener
import no.nav.eessi.pensjon.models.SedHendelseModel
import no.nav.eessi.pensjon.pdl.integrationtest.IntegrationBase
import no.nav.eessi.pensjon.pdl.integrationtest.KafkaTestConfig
import no.nav.eessi.pensjon.pdl.integrationtest.PDL_PRODUSENT_TOPIC_MOTTATT
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockserver.client.MockServerClient
import org.mockserver.integration.ClientAndServer
import org.mockserver.socket.PortFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.util.concurrent.TimeUnit


@SpringBootTest( classes = [KafkaTestConfig::class, IntegrationBase.TestConfig::class])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(
    controlledShutdown = true,
    topics = ["eessi-basis-sedMottatt-v1"]
)
internal class AdresseListenerIT{

    @MockkBean
    lateinit var sedListener: SedListener

    @Autowired
    lateinit var adresseListener: AdresseListener

    @Autowired
    lateinit var producerFactory: ProducerFactory<String, String>

    lateinit var kafkaTemplate: KafkaTemplate<String, String>

    lateinit var mockServer: ClientAndServer

    init {
        if (System.getProperty("mockserverport") == null) {
            mockServer = ClientAndServer(PortFactory.findFreePort())
                .also {
                    System.setProperty("mockserverport", it.localPort.toString())
                }
        }
    }

    @BeforeEach
    fun setup() {
        kafkaTemplate =  KafkaTemplate(producerFactory).apply { defaultTopic = PDL_PRODUSENT_TOPIC_MOTTATT }
    }


    @AfterEach
    fun after() {
        MockServerClient("localhost", System.getProperty("mockserverport").toInt()).reset()
    }


    @Test
    fun `Gitt en sed hendelse som kommer på riktig topic og group_id så skal den konsumeres av adresseListener`() {
        //given
        val mockSedHendelse : SedHendelseModel = mockk(relaxed = true)
        //when
        kafkaTemplate.sendDefault(mockSedHendelse.toJson())
        //then
        adresseListener.latch.await(5, TimeUnit.SECONDS)
        assertEquals(0, adresseListener.latch.count)
    }

    @Test
    fun `Gitt en sed hendelse som finnes i PDL, og adresse er lik så skal ikke PDL oppdateres`() {
        //TODO
    }

    @Test
    fun `Gitt en sed hendelse som finnes i PDL, og adresse er lik så skal PDL oppdaters med ny adresse` () {
        //TODO
    }
}