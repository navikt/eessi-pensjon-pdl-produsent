package no.nav.eessi.pensjon.pdl.integrationtest

import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.personoppslag.pdl.PersonMock
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId
import no.nav.eessi.pensjon.personoppslag.pdl.model.Person
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertTrue

@SpringBootTest( classes = [KafkaTestConfig::class], properties = ["spring.main.allow-bean-definition-overriding=true"])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(
    topics = [PDL_PRODUSENT_TOPIC_MOTTATT],
    brokerProperties = ["log.dir=/tmp/embedded-kafka-NyDanskUtenUidIntegrasjonsTest"]
)

@Disabled // disabler grunnet kafka problemer i Integrasjonstestene
class NyDanskUtenUidIntegrasjonsTest : IntegrationBase() {

    val fnr = "29087021082"

    override fun getMockNorg2enhet() = Enhet.ID_OG_FORDELING

    override fun getMockPerson(): Person {
        return PersonMock.createWith(
            fnr = fnr,
            aktoerId = AktoerId("1231231231"),
            uid = emptyList()
        )
    }

    @Test
    fun `Gitt en sed hendelse med uten dansk uid som ikke finnes i pdl skal det ack med logg Ingen utenlandske IDer funnet i BUC`() {

        val listOverSeder = listOf(mockForenkletSed("eb938171a4cb4e658b3a6c011962d204", SedType.P15000, SedStatus.RECEIVED))
        val mockBuc = mockBuc("147729", BucType.P_BUC_10, listOverSeder)

        val mockPin = mockPin(fnr, "NO")
        val mockSed = mockSedUtenPensjon(sedType = SedType.P15000, pin = listOf(mockPin))

        CustomMockServer()
            .mockSTSToken()
            .medMockSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d204", mockSed)
            .medMockBuc("/buc/147729", mockBuc)
            .medKodeverk("/api/v1/hierarki/LandkoderSammensattISO2/noder", "src/test/resources/kodeverk/landkoderSammensattIso2.json")

        val hendelseJson = mockHendlese(bucType = BucType.P_BUC_10, sedType = SedType.P15000, docId = "eb938171a4cb4e658b3a6c011962d204")

        initAndRunContainer(PDL_PRODUSENT_TOPIC_MOTTATT).also {
            it.sendMsgOnDefaultTopic(hendelseJson)
            it.waitForlatch(sedListener)
        }

        assertTrue(validateSedMottattListenerLoggingMessage("Ingen utenlandske IDer funnet i BUC"))
    }

}

