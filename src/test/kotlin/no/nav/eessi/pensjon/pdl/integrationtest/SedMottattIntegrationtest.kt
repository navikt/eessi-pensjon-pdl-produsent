package no.nav.eessi.pensjon.pdl.integrationtest

import io.mockk.mockk
import no.nav.eessi.pensjon.eux.EuxKlient
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.listeners.SedMottattListener
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.util.concurrent.TimeUnit

const val PDL_PRODUSENT_TOPIC_MOTATT = "eessi-pensjon-pdl-produsent-sed-mottatt"

@SpringBootTest()
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(
    topics = [PDL_PRODUSENT_TOPIC_MOTATT]
)
class SedMottattIntegrationtest : IntegrationBase() {

    val personMottakKlient: PersonMottakKlient = mockk(relaxed = true)

    @Autowired
    lateinit var consumeSedMottatt: SedMottattListener

    @Autowired
    private lateinit var template: KafkaTemplate<String, String>

    @Autowired
    lateinit var euxKlient: EuxKlient

    @Test
    fun `En sed hendelse skal sendes videre til riktig kanal  `() {
       CustomMockServer()
            .mockSTSToken()
            .medBuc("/buc/147729", "src/test/resources/buc/bucMedP6000.json")
            .medBuc("/buc/147729/sed/ae000ec3d718416a934e94e22c844ba6", "src/test/resources/sed/P6000-komplett.json")

        val json = this::class.java.classLoader.getResource("eux/P_BUC_01_P2000.json")!!.readText()
        val model = mapJsonToAny(json, typeRefs())

        template.send(PDL_PRODUSENT_TOPIC_MOTATT, model.toJson()).let {
            consumeSedMottatt.getLatch().await(10, TimeUnit.SECONDS)
        }

    }
}
