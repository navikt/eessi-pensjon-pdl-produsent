package no.nav.eessi.pensjon.pdl.integrationtest

import io.mockk.every
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.personoppslag.pdl.PersonMock
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import org.junit.jupiter.api.Disabled
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
    topics = [PDL_PRODUSENT_TOPIC_MOTTATT],
    brokerProperties = ["log.dir=/tmp/embedded-kafka-NyTyskUidMedFlereUidSenderTyskInstIntegrasjonsTest"]
)

@Disabled // Disabled grunnet ambigous kafka
class NyTyskUidMedFlereUidSenderTyskInstIntegrasjonsTest : IntegrationBase() {

    @Test
    fun `Gitt en sed-hendelse fra Tyskland med flere uid i sed så skal det stoppes av valideringen`() {

        val fnr = "29087021082"
        val personMock =  PersonMock.createBrukerWithUid(
            fnr = fnr,
            uid = emptyList()
        )

        val listOverSeder = listOf(mockForenkletSed("eb938171a4cb4e658b3a6c011962d204", SedType.P8000, SedStatus.RECEIVED))
        val mockBuc = mockBuc("147729", BucType.P_BUC_02, listOverSeder)

        every { personService.hentPersonUtenlandskIdent(NorskIdent(fnr)) } returns personMock
        CustomMockServer()
            .mockSTSToken()
            .medSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d204", "src/test/resources/eux/sed/P8000-TyskOgFinskPIN.json")
            .medMockBuc("/buc/147729", mockBuc)
            .medKodeverk("/api/v1/hierarki/LandkoderSammensattISO2/noder", "src/test/resources/kodeverk/landkoderSammensattIso2.json")

        val json = javaClass.getResource("/eux/hendelser/P_BUC_01_P2000-avsenderDE.json")!!.readText()

        initAndRunContainer(PDL_PRODUSENT_TOPIC_MOTTATT).also {
            it.sendMsgOnDefaultTopic(json)
            it.waitForlatch(sedListener)
        }

        assertTrue(validateSedMottattListenerLoggingMessage("Antall utenlandske IDer er flere enn en"))
        mockServer.verify(
            HttpRequest.request()
                .withMethod("POST")
                .withPath("/api/v1/endringer"),
            VerificationTimes.exactly(0)
        )
    }
}
