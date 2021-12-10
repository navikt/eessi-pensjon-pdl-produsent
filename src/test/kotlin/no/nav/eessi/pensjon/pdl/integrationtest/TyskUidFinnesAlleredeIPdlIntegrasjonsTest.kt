package no.nav.eessi.pensjon.pdl.integrationtest

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.mockk.every
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.listeners.SedMottattListener
import no.nav.eessi.pensjon.personoppslag.pdl.PersonMock
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskIdentifikasjonsnummer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockserver.model.HttpRequest
import org.mockserver.verify.VerificationTimes
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

const val PDL_PRODUSENT_TOPIC_MOTATT = "eessi-basis-sedmottatt-v1"

@SpringBootTest( classes = [IntegrationBase.TestConfig::class, KafkaTestConfig::class], properties = ["spring.main.allow-bean-definition-overriding=true"])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(
    topics = [PDL_PRODUSENT_TOPIC_MOTATT2]
)
class SedMottattIntegrationtest : IntegrationBase() {

    @Autowired(required = true)
    private lateinit var sedMottattListener: SedMottattListener

    @Autowired
    private lateinit var template: KafkaTemplate<String, String>

    @Autowired
    private lateinit var personService: PersonService

    private val deugLogger: Logger = LoggerFactory.getLogger("no.nav.eessi.pensjon") as Logger
    private val listAppender = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun setUp() {
        listAppender.start()
        deugLogger.addAppender(listAppender)
    }

    @Disabled
    @Test
    fun `Gitt en sed hendelse med tysk uid i sed som også finnes i pdl så skal vi acke og avslutte på en pen måte`() {

        val fnr = "11067122781"
        val personMock =  PersonMock.createBrukerWithUid(
            fnr = fnr,
            uid = listOf(UtenlandskIdentifikasjonsnummer(
                identifikasjonsnummer = "130177-1234",
                utstederland = "DEU",
                opphoert = false,
                metadata = PersonMock.createMetadata()
            ))
        )

        every { personService.hentPersonUtenlandskIdent(NorskIdent(fnr)) } returns personMock
        CustomMockServer()
            .mockSTSToken()
            .medSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d204", "src/test/resources/eux/sed/P2100-PinDK-NAV.json")
            .medbBuc("/buc/147729", "src/test/resources/eux/buc/buc279020.json")
            .medKodeverk("/api/v1/hierarki/LandkoderSammensattISO2/noder", "src/test/resources/kodeverk/landkoderSammensattIso2.json")

        val json = javaClass.getResource("/eux/hendelser/P_BUC_01_P2000-avsenderDK.json")!!.readText()
        val model = mapJsonToAny(json, typeRefs())

        template.send(PDL_PRODUSENT_TOPIC_MOTATT2, model.toJson()).let {
            sedMottattListener.getLatch().await(10, TimeUnit.SECONDS)
        }

         assertTrue(validateSedMottattListenerLoggingMessage("Ingen filtrerte personer funnet Acket sedMottatt"))

        mockServer.verify(
            HttpRequest.request()
                .withMethod("POST")
                .withPath("/api/v1/endringer"),
            VerificationTimes.exactly(0)
        )

    }

//    @Test
//    fun `Gitt en sed hendelse med dansk uid som ikke finnes i pdl skal det opprettes det en endringsmelding til person-mottak`() {
//
//        val fnr = "11067122781"
//        val personMock =  PersonMock.createBrukerWithUid(
//            fnr = fnr,
//            uid = emptyList()
//        )
//
//        every { personService.hentPersonUtenlandskIdent(NorskIdent(fnr)) } returns personMock
//        CustomMockServer()
//            .mockSTSToken()
//            .medSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d204", "src/test/resources/eux/sed/P2100-PinDK-NAV.json")
//            .medbBuc("/buc/147729", "src/test/resources/eux/buc/buc279020.json")
//            .medKodeverk("/api/v1/hierarki/LandkoderSammensattISO2/noder", "src/test/resources/kodeverk/landkoderSammensattIso2.json")
//
//        val json = javaClass.getResource("/eux/hendelser/P_BUC_01_P2000-avsenderDK.json")!!.readText()
//        val model = mapJsonToAny(json, typeRefs())
//
//        template.send(PDL_PRODUSENT_TOPIC_MOTATT2, model.toJson()).let {
//            sedMottattListener.getLatch().await(10, TimeUnit.SECONDS)
//
//        assertTrue(validateSedMottattListenerLoggingMessage("Oppretter endringsmelding med nye personopplysninger fra avsenderLand:"))
//
//            mockServer.verify(
//                HttpRequest.request()
//                    .withMethod("POST")
//                    .withPath("/api/v1/endringer"),
//                VerificationTimes.exactly(1)
//            )
//
//        }
//    }
//
//    @Test
//    fun `Gitt en sed hendelse med tysk uid som ikke finnes i pdl når P8000 prosesseres så opprettes det en endringsmelding til person-mottak`() {
//
//        val fnr = "29087021082"
//        val personMock =  PersonMock.createBrukerWithUid(
//            fnr = fnr,
//            uid = emptyList()
//        )
//
//        every { personService.hentPersonUtenlandskIdent(NorskIdent(fnr)) } returns personMock
//        CustomMockServer()
//            .mockSTSToken()
//            .medSed("/buc/147729/sed/b12e06dda2c7474b9998c7139c841646", "src/test/resources/eux/sed/P8000-TyskPIN.json")
//            .medSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d204", "src/test/resources/eux/sed/P8000-TyskPIN.json")
//            .medbBuc("/buc/147729", "src/test/resources/eux/buc/buc279020.json")
//            .medKodeverk("/api/v1/hierarki/LandkoderSammensattISO2/noder", "src/test/resources/kodeverk/landkoderSammensattIso2.json")
//
//        val json = javaClass.getResource("/eux/hendelser/P_BUC_01_P2000-avsenderDE.json")!!.readText()
//        val model = mapJsonToAny(json, typeRefs())
//
//        template.send(PDL_PRODUSENT_TOPIC_MOTATT2, model.toJson()).let {
//            sedMottattListener.getLatch().await(10, TimeUnit.SECONDS)
//        }
//
//        mockServer.verify(
//            HttpRequest.request()
//                .withMethod("POST")
//                .withPath("/api/v1/endringer")
//                .withBody("""
//                    {
//                      "personopplysninger" : [ {
//                        "endringstype" : "OPPRETT",
//                        "ident" : "29087021082",
//                        "opplysningstype" : "UTENLANDSKIDENTIFIKASJONSNUMMER",
//                        "endringsmelding" : {
//                          "@type" : "UTENLANDSKIDENTIFIKASJONSNUMMER",
//                          "identifikasjonsnummer" : "56 120157 F 016",
//                          "utstederland" : "DEU",
//                          "kilde" : "NAVT003"
//                        },
//                        "opplysningsId" : null
//                      } ]
//                    }
//                """.trimIndent()),
//            VerificationTimes.exactly(1)
//        )
//    }
//
//    @Test
//    fun `Gitt en sed-hendelse med tysk uid som mangler institusjonsnavn så skal den stoppes av validering`() {
//
//        val fnr = "29087021082"
//        val personMock =  PersonMock.createBrukerWithUid(
//            fnr = fnr,
//            uid = emptyList()
//        )
//
//        every { personService.hentPersonUtenlandskIdent(NorskIdent(fnr)) } returns personMock
//        CustomMockServer()
//            .mockSTSToken()
//            .medSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d204", "src/test/resources/eux/sed/P8000-TyskPIN.json")
//            .medbBuc("/buc/147729", "src/test/resources/eux/buc/buc279020.json")
//            .medKodeverk("/api/v1/hierarki/LandkoderSammensattISO2/noder", "src/test/resources/kodeverk/landkoderSammensattIso2.json")
//
//        val json = javaClass.getResource("/eux/hendelser/P_BUC_01_P2000-utenland.json")!!.readText()
//        val model = mapJsonToAny(json, typeRefs())
//
//        template.send(PDL_PRODUSENT_TOPIC_MOTATT2, model.toJson()).let {
//            sedMottattListener.getLatch().await(10, TimeUnit.SECONDS)
//        }
//
//        assertTrue(validateSedMottattListenerLoggingMessage("Avsenderland mangler eller avsenderland er ikke det samme som uidland, stopper identifisering av personer"))
//
//        mockServer.verify(
//            HttpRequest.request()
//                .withMethod("POST")
//                .withPath("/api/v1/endringer"),
//            VerificationTimes.exactly(0)
//        )
//    }
//
//    @Test
//    fun `Gitt en sed-hendelse fra Sverige som sender inn en tysk uid så skal det stoppes av valideringen`() {
//
//        val fnr = "29087021082"
//        val personMock =  PersonMock.createBrukerWithUid(
//            fnr = fnr,
//            uid = emptyList()
//        )
//
//        every { personService.hentPersonUtenlandskIdent(NorskIdent(fnr)) } returns personMock
//        CustomMockServer()
//            .mockSTSToken()
//            .medSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d204", "src/test/resources/eux/sed/P8000-TyskPIN.json")
//            .medbBuc("/buc/147729", "src/test/resources/eux/buc/buc279020.json")
//            .medKodeverk("/api/v1/hierarki/LandkoderSammensattISO2/noder", "src/test/resources/kodeverk/landkoderSammensattIso2.json")
//
//        val json = javaClass.getResource("/eux/hendelser/P_BUC_01_P2000-avsenderSE.json")!!.readText()
//        val model = mapJsonToAny(json, typeRefs())
//
//        template.send(PDL_PRODUSENT_TOPIC_MOTATT2, model.toJson()).let {
//            sedMottattListener.getLatch().await(10, TimeUnit.SECONDS)
//        }
//
//        assertTrue(validateSedMottattListenerLoggingMessage("Avsenderland mangler eller avsenderland er ikke det samme som uidland, stopper identifisering av personer"))
//
//        mockServer.verify(
//            HttpRequest.request()
//                .withMethod("POST")
//                .withPath("/api/v1/endringer"),
//            VerificationTimes.exactly(0)
//        )
//    }
//
//    @Test
//    fun `Gitt en sed-hendelse fra Tyskland med flere uid i sed så skal det stoppes av valideringen`() {
//
//        val fnr = "29087021082"
//        val personMock =  PersonMock.createBrukerWithUid(
//            fnr = fnr,
//            uid = emptyList()
//        )
//
//        every { personService.hentPersonUtenlandskIdent(NorskIdent(fnr)) } returns personMock
//        CustomMockServer()
//            .mockSTSToken()
//            .medSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d204", "src/test/resources/eux/sed/P8000-TyskOgFinskPIN.json")
//            .medbBuc("/buc/147729", "src/test/resources/eux/buc/buc279020.json")
//            .medKodeverk("/api/v1/hierarki/LandkoderSammensattISO2/noder", "src/test/resources/kodeverk/landkoderSammensattIso2.json")
//
//        val json = javaClass.getResource("/eux/hendelser/P_BUC_01_P2000-avsenderDE.json")!!.readText()
//        val model = mapJsonToAny(json, typeRefs())
//
//        template.send(PDL_PRODUSENT_TOPIC_MOTATT2, model.toJson()).let {
//            sedMottattListener.getLatch().await(10, TimeUnit.SECONDS)
//        }
//
//        assertTrue(validateSedMottattListenerLoggingMessage("Antall utenlandske IDer er flere enn en"))
//        mockServer.verify(
//            HttpRequest.request()
//                .withMethod("POST")
//                .withPath("/api/v1/endringer"),
//            VerificationTimes.exactly(0)
//        )
//    }

    fun validateSedMottattListenerLoggingMessage(keyword: String): Boolean {
        val logsList: List<ILoggingEvent> = listAppender.list
        return logsList.find { logMelding ->
            logMelding.message.contains(keyword)
        }?.message?.isNotEmpty() ?: false
    }
}
