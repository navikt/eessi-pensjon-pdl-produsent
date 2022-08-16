package no.nav.eessi.pensjon.pdl.adresseoppdatering

import io.mockk.mockk
import no.nav.eessi.pensjon.models.SedHendelse
import no.nav.eessi.pensjon.pdl.integrationtest.IntegrationBase
import no.nav.eessi.pensjon.pdl.integrationtest.KafkaTestConfig
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
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
class SedListenerAdresseIT : IntegrationBase() {

    @Autowired
    lateinit var adresseListener: SedListenerAdresse

    @Test
    fun `Gitt en sed hendelse som kommer på riktig topic og group_id så skal den konsumeres av adresseListener`() {
        val mockSedHendelse: SedHendelse = mockk(relaxed = true)

        kafkaTemplate.sendDefault(mockSedHendelse.toJson())

        adresseListener.latch.await(5, TimeUnit.SECONDS)
        assertEquals(0, adresseListener.latch.count)
    }
}