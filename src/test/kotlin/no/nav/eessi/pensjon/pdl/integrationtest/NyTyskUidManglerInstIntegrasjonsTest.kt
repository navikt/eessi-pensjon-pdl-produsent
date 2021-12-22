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
    topics = [PDL_PRODUSENT_TOPIC_MOTATT],
    brokerProperties = ["log.dir=/tmp/embedded-kafka-NyTyskUidManglerInstIntegrasjonsTest"]
)
class NyTyskUidManglerInstIntegrasjonsTest : IntegrationBase() {

    @Test
    fun `Gitt en sed-hendelse med tysk uid som mangler institusjonsnavn så skal den stoppes av validering`() {

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
            .medSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d204", "src/test/resources/eux/sed/P8000-TyskPIN.json")
            .medMockBuc("/buc/147729", mockBuc)
            .medKodeverk("/api/v1/hierarki/LandkoderSammensattISO2/noder", "src/test/resources/kodeverk/landkoderSammensattIso2.json")

        val json = javaClass.getResource("/eux/hendelser/P_BUC_01_P2000-utenland.json")!!.readText()

        initAndRunContainer(PDL_PRODUSENT_TOPIC_MOTATT).also {
            it.sendMsgOnDefaultTopic(json)
            it.waitForlatch(sedListener)
        }

        assertTrue(validateSedMottattListenerLoggingMessage("Avsenderland mangler eller avsenderland er ikke det samme som uidland, stopper identifisering av personer"))

        mockServer.verify(
            HttpRequest.request()
                .withMethod("POST")
                .withPath("/api/v1/endringer"),
            VerificationTimes.exactly(0)
        )
    }
}
