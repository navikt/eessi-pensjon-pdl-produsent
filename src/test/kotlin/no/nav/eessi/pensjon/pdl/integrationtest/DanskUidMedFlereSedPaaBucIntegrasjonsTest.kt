package no.nav.eessi.pensjon.pdl.integrationtest

import io.mockk.every
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.personoppslag.pdl.PersonMock
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import org.junit.jupiter.api.Test
import org.mockserver.model.HttpRequest
import org.mockserver.verify.VerificationTimes
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertTrue

@SpringBootTest( classes = [KafkaTestConfig::class], properties = ["spring.main.allow-bean-definition-overriding=true"])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(
    topics = [PDL_PRODUSENT_TOPIC_MOTATT, PDL_PRODUSENT_TOPIC_SENDT],
    brokerProperties = ["log.dir=/tmp/embedded-kafka-DanskUidMedFlereSedPaaBucIntegrasjonsTest"]
)
class DanskUidMedFlereSedPaaBucIntegrasjonsTest : IntegrationBase() {

    @Test
    fun `Gitt en hendelse med flere sed i buc og en dansk uid som ikke finnes i pdl skal det opprettes det en endringsmelding til person-mottak`() {

        val fnr = "11067122781"
        val personMock = PersonMock.createBrukerWithUid(
            fnr = fnr,
            uid = emptyList()
        )

        val listOverSeder = listOf(
            mockForenkletSed("eb938171a4cb4e658b3a6c011962d204", SedType.P2100, SedStatus.RECEIVED),
            mockForenkletSed("eb938171a4cb4e658b3a6c011962d205", SedType.P5000, SedStatus.SENT),
            mockForenkletSed("eb938171a4cb4e658b3a6c011962d504", SedType.P7000, SedStatus.RECEIVED),
            mockForenkletSed("eb938171a4cb4e658b3a6c011962d205", SedType.H120, SedStatus.RECEIVED)
        )
        val mockBuc = mockBuc("147729", BucType.P_BUC_02, listOverSeder)

        every { personService.hentPersonUtenlandskIdent(NorskIdent(fnr)) } returns personMock
        CustomMockServer()
            .mockSTSToken()
            .medSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d204", "src/test/resources/eux/sed/P2100-PinDK-NAV.json")
            .medSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d205", "src/test/resources/eux/sed/P5000-NAV.json")
            .medSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d504", "src/test/resources/eux/sed/P7000-NAV.json")
            .medMockBuc("/buc/147729", mockBuc)
            .medKodeverk("/api/v1/hierarki/LandkoderSammensattISO2/noder", "src/test/resources/kodeverk/landkoderSammensattIso2.json")

        val hendelseJson = mockHendlese(bucType = BucType.P_BUC_02, sedType = SedType.P7000, docId = "eb938171a4cb4e658b3a6c011962d504")


        initAndRunContainer(PDL_PRODUSENT_TOPIC_MOTATT).also {
            it.sendMsgOnDefaultTopic(hendelseJson)
            it.waitForlatch(sedListener)
        }

        assertTrue(validateSedMottattListenerLoggingMessage("SED av type: P2100, status: RECEIVED"))
        assertTrue(validateSedMottattListenerLoggingMessage("SED av type: P5000, status: SENT"))
        assertTrue(validateSedMottattListenerLoggingMessage("SED av type: P7000, status: RECEIVED"))
        assertTrue(validateSedMottattListenerLoggingMessage("Oppretter endringsmelding med nye personopplysninger fra avsenderLand:"))

        mockServer.verify(
            HttpRequest.request()
                .withMethod("POST")
                .withPath("/api/v1/endringer"),
            VerificationTimes.exactly(1)
        )
    }
}

