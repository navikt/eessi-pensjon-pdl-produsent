package no.nav.eessi.pensjon.pdl.integrajontest

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.eux.EuxKlient
import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import no.nav.eessi.pensjon.statistikk.integrationtest.IntegrationBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.util.concurrent.TimeUnit

const val PDL_PRODUSENT_TOPIC_MOTATT = "eessi-pensjon-statistikk-sed-mottatt"

@SpringBootTest(classes = [IntegrationBase.TestConfig::class])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(
    topics = [PDL_PRODUSENT_TOPIC_MOTATT]
)
class SedMottattIntegrasjonsTest : IntegrationBase() {

    val personMottakKlient: PersonMottakKlient = mockk(relaxed = true)

    @Autowired
    lateinit var statistikkListener: StatistikkListener

    @Autowired
    lateinit var statistikkPublisher: StatistikkPublisher

    @Autowired
    private lateinit var template: KafkaTemplate<String, String>

    @Autowired
    lateinit var euxKlient: EuxKlient

    @Test
    fun `En sed hendelse skal sendes videre til riktig kanal  `() {
        euxKlient.initMetrics()

        CustomMockServer()
            .mockSTSToken()
            .medBuc("/buc/147729", "src/test/resources/buc/bucMedP6000.json")
            .medBuc("/buc/147729/sed/ae000ec3d718416a934e94e22c844ba6", "src/test/resources/sed/P6000-komplett.json")

        val bucMetadata  = BucMetadata (listOf(), BucType.P_BUC_01, "2020-12-08T09:52:55.345+0000")

        every{ euxService.getBucMetadata(any()) } returns bucMetadata

        val sedHendelse = ResourceHelper.getResourceSedHendelseRina("eux/P_BUC_01_P2000.json").toJson()
        val model = mapJsonToAny(sedHendelse, typeRefs<SedHendelseRina>())

        template.send(STATISTIKK_TOPIC_MOTATT, model.toJson()).let {
            statistikkListener.getLatch().await(10, TimeUnit.SECONDS)
        }

        verify(exactly = 1) { statistikkPublisher.publiserSedHendelse(eq(sedMeldingP6000Ut())) }
    }


    private fun sedMeldingP6000Ut(): SedMeldingP6000Ut {
        val meldingUtJson = """
            {
              "dokumentId" : "ae000ec3d718416a934e94e22c844ba6",
              "bucType" : "P_BUC_06",
              "rinaid" : "147729",
              "mottakerLand" : [ "NO" ],
              "avsenderLand" : "NO",
              "rinaDokumentVersjon" : "1",
              "sedType" : "P6000",
              "pid" : "09028020144",
              "hendelseType" : "SED_MOTTATT",
              "pesysSakId" : "22919968",
              "opprettetTidspunkt" : "2021-02-11T13:08:03Z",
              "vedtaksId" : null,
              "bostedsland" : "HR",
              "pensjonsType" : "GJENLEV",
              "vedtakStatus" : "FORELOPIG_UTBETALING",
              "bruttoBelop" : "12482",
              "valuta" : "NOK"
            }
        """.trimIndent()
        return mapJsonToAny(meldingUtJson, typeRefs())
    }
}
