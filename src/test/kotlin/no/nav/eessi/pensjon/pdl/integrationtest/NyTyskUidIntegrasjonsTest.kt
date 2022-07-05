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
import org.mockserver.model.HttpRequest
import org.mockserver.verify.VerificationTimes
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.junit.jupiter.api.Assertions.assertTrue

@SpringBootTest( classes = [KafkaTestConfig::class])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(
    topics = [PDL_PRODUSENT_TOPIC_MOTTATT],
    brokerProperties = ["log.dir=build/kafka/embedded-kafka-NyTyskUidIntegrasjonsTest"]
)

@Disabled // disabler grunnet kafka problemer i Integrasjonstestene
class NyTyskUidIntegrasjonsTest : IntegrationBase() {

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
    fun `Gitt en sed-hendelse fra Sverige som sender inn en tysk uid så skal det stoppes av valideringen`() {

        val listOverSeder = listOf(mockForenkletSed("eb938171a4cb4e658b3a6c011962d204", SedType.P8000, SedStatus.RECEIVED))
        val mockBuc = mockBuc("147729", BucType.P_BUC_02, listOverSeder)

        CustomMockServer()
            .mockSTSToken()
            .medSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d204", "src/test/resources/eux/sed/P8000-TyskPIN.json")
            .medMockBuc("/buc/147729", mockBuc)
            .medKodeverk("/api/v1/hierarki/LandkoderSammensattISO2/noder", "src/test/resources/kodeverk/landkoderSammensattIso2.json")

        val json = javaClass.getResource("/eux/hendelser/P_BUC_01_P2000-avsenderSE.json")!!.readText()

        initAndRunContainer(PDL_PRODUSENT_TOPIC_MOTTATT).also {
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
