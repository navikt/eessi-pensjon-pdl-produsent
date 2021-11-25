package no.nav.eessi.pensjon.pdl.integrationtest

import com.ninjasquad.springmockk.MockkBean
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.EuxDokumentHelper
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.listeners.SedMottattListener
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.util.concurrent.TimeUnit

const val PDL_PRODUSENT_TOPIC_MOTATT = "eessi-pensjon-pdl-produsent-sed-mottatt"

@SpringBootTest( classes = [KafkaConfigIntegrationtest::class])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(
    topics = [PDL_PRODUSENT_TOPIC_MOTATT]
)
class SedMottattIntegrationtest : IntegrationBase() {

    val personMottakKlient: PersonMottakKlient = mockk(relaxed = true)

    @Autowired
    private lateinit var template: KafkaTemplate<String, String>

    @MockkBean
    private  lateinit var kodeverkClient : KodeverkClient

    @MockkBean
    private lateinit var personidentifiseringService: PersonidentifiseringService

    private val dokumentHelper = EuxDokumentHelper(mockk())

    protected val sedMottattListener: SedMottattListener = SedMottattListener(
        personidentifiseringService = personidentifiseringService,
        dokumentHelper = dokumentHelper,
        personMottakKlient = personMottakKlient,
        profile = "test"
    )

    @Test
    fun `En sed hendelse skal sendes videre til riktig kanal  `() {
       CustomMockServer()
            .mockSTSToken()
            .medSed("/buc/147729/sed/b12e06dda2c7474b9998c7139c841646", "src/test/resources/eux/sed/P2100-PinDK-NAV.json")

        val json = this::class.java.classLoader.getResource("eux/hendelser/P_BUC_01_P2000.json")!!.readText()
        val model = mapJsonToAny(json, typeRefs())

        template.send(PDL_PRODUSENT_TOPIC_MOTATT, model.toJson()).let {
            sedMottattListener.getLatch().await(10, TimeUnit.SECONDS)
        }

    }
}
