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
    brokerProperties = ["log.dir=build/kafka/embedded-kafka-DanskUidMedFlereSedPaaBucIntegrasjonsTest"]
)

@Disabled // disabler grunnet kafka problemer i Integrasjonstestene
class DanskUidMedFlereSedPaaBucIntegrasjonsTest : IntegrationBase() {

    val fnr = "11067122781"

    override fun getMockNorg2enhet(): Enhet {
        return Enhet.NFP_UTLAND_OSLO
    }

    override fun getMockPerson(): Person {
        return PersonMock.createWith(
            fnr = fnr,
            aktoerId = AktoerId("1231231231"),
            uid = emptyList()
        )
    }

    @Test
    fun `Gitt en hendelse med flere sed i buc og en dansk uid som ikke finnes i pdl skal det opprettes det en endringsmelding til person-mottak`() {

        val listOverSeder = listOf(
            mockForenkletSed("eb938171a4cb4e658b3a6c011962d204", SedType.P2100, SedStatus.RECEIVED),
            mockForenkletSed("eb938171a4cb4e658b3a6c011962d205", SedType.P5000, SedStatus.SENT),
            mockForenkletSed("eb938171a4cb4e658b3a6c011962d504", SedType.P7000, SedStatus.RECEIVED),
            mockForenkletSed("eb938171a4cb4e658b3a6c011962d205", SedType.H120, SedStatus.RECEIVED)
        )
        val mockBuc = mockBuc("147729", BucType.P_BUC_02, listOverSeder)

        CustomMockServer()
            .mockSTSToken()
            .medSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d204", "src/test/resources/eux/sed/P2100-PinDK-NAV.json")
            .medSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d205", "src/test/resources/eux/sed/P5000-NAV.json")
            .medSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d504", "src/test/resources/eux/sed/P7000-NAV.json")
            .medMockBuc("/buc/147729", mockBuc)
            .medKodeverk("/api/v1/hierarki/LandkoderSammensattISO2/noder", "src/test/resources/kodeverk/landkoderSammensattIso2.json")
            .medEndring()

        val hendelseJson = mockHendlese(bucType = BucType.P_BUC_02, sedType = SedType.P7000, docId = "eb938171a4cb4e658b3a6c011962d504")


        initAndRunContainer(PDL_PRODUSENT_TOPIC_MOTTATT).also {
            it.sendMsgOnDefaultTopic(hendelseJson)
            it.waitForlatch(sedListener)
        }

        assertTrue(validateSedMottattListenerLoggingMessage("SED av type: P2100, status: RECEIVED"))
        assertTrue(validateSedMottattListenerLoggingMessage("SED av type: P5000, status: SENT"))
        assertTrue(validateSedMottattListenerLoggingMessage("SED av type: P7000, status: RECEIVED"))
        assertTrue(validateSedMottattListenerLoggingMessage("Oppretter endringsmelding med nye personopplysninger fra avsenderLand:"))

        val check = """
              "personopplysninger" : [ {
                "endringstype" : "OPPRETT",
                "ident" : "11067122781",
                "opplysningstype" : "UTENLANDSKIDENTIFIKASJONSNUMMER",
                "endringsmelding" : {
                  "@type" : "UTENLANDSKIDENTIFIKASJONSNUMMER",
                  "identifikasjonsnummer" : "130177-1234",
                  "utstederland" : "DNK",
                  "kilde" : "DK:D005"
                },
                "opplysningsId" : null
              } ]
            }
        """.trimIndent()

        assertTrue(validateSedMottattListenerLoggingMessage(check))

        mockServer.verify(
            HttpRequest.request()
                .withMethod("POST")
                .withPath("/api/v1/endringer"),
            VerificationTimes.exactly(1)
        )
    }

}

