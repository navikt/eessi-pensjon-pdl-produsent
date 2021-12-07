package no.nav.eessi.pensjon.pdl.integrationtest

import io.mockk.every
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.listeners.SedMottattListener
import no.nav.eessi.pensjon.personoppslag.pdl.PersonMock
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskIdentifikasjonsnummer
import org.junit.jupiter.api.Test
import org.mockserver.model.HttpRequest
import org.mockserver.verify.VerificationTimes
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.util.concurrent.TimeUnit

const val PDL_PRODUSENT_TOPIC_MOTATT = "eessi-basis-sedmottatt-v1"

@SpringBootTest( classes = [IntegrationBase.TestConfig::class, KafkaTestConfig::class], properties = ["spring.main.allow-bean-definition-overriding=true"])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(
    topics = [PDL_PRODUSENT_TOPIC_MOTATT]
)
class SedMottattIntegrationtest : IntegrationBase() {

    @Autowired(required = true)
    private lateinit var sedMottattListener: SedMottattListener

    @Autowired
    private lateinit var template: KafkaTemplate<String, String>

    @Autowired
    private lateinit var personService: PersonService

    @Test
    fun `En sed hendelse med dansk uid i sed finnes også i pdl skal ack og avslute på en pen måte`() {

        val fnr = "11067122781"
        val personMock =  PersonMock.createBrukerWithUid(
            fnr = fnr,
            uid = listOf(UtenlandskIdentifikasjonsnummer(
                identifikasjonsnummer = "130177-1234",
                utstederland = "DNK",
                opphoert = false,
                metadata = PersonMock.createMetadata()
            ))
        )

        every { personService.hentPersonUtenlandskIdent(NorskIdent(fnr)) } returns personMock
        CustomMockServer()
            .mockSTSToken()
            .medSed("/buc/147729/sed/b12e06dda2c7474b9998c7139c841646", "src/test/resources/eux/sed/P2100-PinDK-NAV.json")
            .medKodeverk("/api/v1/hierarki/LandkoderSammensattISO2/noder", "src/test/resources/kodeverk/landkoderSammensattIso2.json")

        val json = javaClass.getResource("/eux/hendelser/P_BUC_01_P2000.json")!!.readText()
        val model = mapJsonToAny(json, typeRefs())

        template.send(PDL_PRODUSENT_TOPIC_MOTATT, model.toJson()).let {
            sedMottattListener.getLatch().await(10, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `En sed hendelse med dansk uid finnes ikke i pdl skal opperte en endringsmelding til person-mottak`() {

        val fnr = "11067122781"
        val personMock =  PersonMock.createBrukerWithUid(
            fnr = fnr,
            uid = emptyList()
        )

        every { personService.hentPersonUtenlandskIdent(NorskIdent(fnr)) } returns personMock
        CustomMockServer()
            .mockSTSToken()
            .medSed("/buc/147729/sed/b12e06dda2c7474b9998c7139c841646", "src/test/resources/eux/sed/P2100-PinDK-NAV.json")
            .medKodeverk("/api/v1/hierarki/LandkoderSammensattISO2/noder", "src/test/resources/kodeverk/landkoderSammensattIso2.json")

        val json = javaClass.getResource("/eux/hendelser/P_BUC_01_P2000.json")!!.readText()
        val model = mapJsonToAny(json, typeRefs())

        template.send(PDL_PRODUSENT_TOPIC_MOTATT, model.toJson()).let {
            sedMottattListener.getLatch().await(10, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `Gitt en sed-hendelse med tysk uid finnes ikke i pdl når P8000 prosesserers så opprettes det en endringsmelding til person-mottak`() {

        val fnr = "29087021082"
        val personMock =  PersonMock.createBrukerWithUid(
            fnr = fnr,
            uid = emptyList()
        )

        every { personService.hentPersonUtenlandskIdent(NorskIdent(fnr)) } returns personMock
        CustomMockServer()
            .mockSTSToken()
            .medSed("/buc/147729/sed/b12e06dda2c7474b9998c7139c841646", "src/test/resources/eux/sed/P8000-TyskPIN.json")
            .medKodeverk("/api/v1/hierarki/LandkoderSammensattISO2/noder", "src/test/resources/kodeverk/landkoderSammensattIso2.json")

        val json = javaClass.getResource("/eux/hendelser/P_BUC_01_P2000-avsenderDE.json")!!.readText()
        val model = mapJsonToAny(json, typeRefs())

        template.send(PDL_PRODUSENT_TOPIC_MOTATT, model.toJson()).let {
            sedMottattListener.getLatch().await(10, TimeUnit.SECONDS)
        }

        mockServer.verify(
            HttpRequest.request()
                .withMethod("POST")
                .withPath("/api/v1/endringer")
                .withBody("""
                    {
                      "personopplysninger" : [ {
                        "endringstype" : "OPPRETT",
                        "ident" : "29087021082",
                        "opplysningstype" : "UTENLANDSKIDENTIFIKASJONSNUMMER",
                        "endringsmelding" : {
                          "@type" : "UTENLANDSKIDENTIFIKASJONSNUMMER",
                          "identifikasjonsnummer" : "56 120157 F 016",
                          "utstederland" : "DEU",
                          "kilde" : "NAVT003"
                        },
                        "opplysningsId" : null
                      } ]
                    }
                """.trimIndent()),
            VerificationTimes.exactly(1)
        )
    }

    @Test
    fun `Gitt en sed-hendelse med tysk uid som mangler institusjon så skal den stoppes av validering`() {

        val fnr = "29087021082"
        val personMock =  PersonMock.createBrukerWithUid(
            fnr = fnr,
            uid = emptyList()
        )

        every { personService.hentPersonUtenlandskIdent(NorskIdent(fnr)) } returns personMock
        CustomMockServer()
            .mockSTSToken()
            .medSed("/buc/147729/sed/b12e06dda2c7474b9998c7139c841646", "src/test/resources/eux/sed/P8000-TyskPIN.json")
            .medKodeverk("/api/v1/hierarki/LandkoderSammensattISO2/noder", "src/test/resources/kodeverk/landkoderSammensattIso2.json")

        val json = javaClass.getResource("/eux/hendelser/P_BUC_01_P2000-utenland.json")!!.readText()
        val model = mapJsonToAny(json, typeRefs())

        template.send(PDL_PRODUSENT_TOPIC_MOTATT, model.toJson()).let {
            sedMottattListener.getLatch().await(10, TimeUnit.SECONDS)
        }

        mockServer.verify(
            HttpRequest.request()
                .withMethod("POST")
                .withPath("/api/v1/endringer"),
            VerificationTimes.exactly(0)
        )
    }

    @Test
    fun `Gitt en sed-hendelse fra Sverige som sender inn en tysk uid så skal det stoppes av valideringen`() {

        val fnr = "29087021082"
        val personMock =  PersonMock.createBrukerWithUid(
            fnr = fnr,
            uid = emptyList()
        )

        every { personService.hentPersonUtenlandskIdent(NorskIdent(fnr)) } returns personMock
        CustomMockServer()
            .mockSTSToken()
            .medSed("/buc/147729/sed/b12e06dda2c7474b9998c7139c841646", "src/test/resources/eux/sed/P8000-TyskOgFinskPIN.json")
            .medKodeverk("/api/v1/hierarki/LandkoderSammensattISO2/noder", "src/test/resources/kodeverk/landkoderSammensattIso2.json")

        val json = javaClass.getResource("/eux/hendelser/P_BUC_01_P2000-avsenderSE.json")!!.readText()
        val model = mapJsonToAny(json, typeRefs())

        template.send(PDL_PRODUSENT_TOPIC_MOTATT, model.toJson()).let {
            sedMottattListener.getLatch().await(10, TimeUnit.SECONDS)
        }

        mockServer.verify(
            HttpRequest.request()
                .withMethod("POST")
                .withPath("/api/v1/endringer"),
            VerificationTimes.exactly(0)
        )
    }
}
