package no.nav.eessi.pensjon.pdl.integrationtest

import com.ninjasquad.springmockk.MockkBean
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.security.sts.STSService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.Header
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse
import org.mockserver.model.HttpStatusCode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpMethod
import org.springframework.kafka.test.EmbeddedKafkaBroker
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

private lateinit var mockServer: ClientAndServer

abstract class IntegrationBase() {

    @Autowired
    lateinit var embeddedKafka: EmbeddedKafkaBroker

    @MockkBean
    lateinit var stsService: STSService

    @BeforeEach
    fun setup() {
        every { stsService.getSystemOidcToken() } returns "a nice little token?"
    }

    @AfterEach
    fun after() {
        println("****************************** after ********************************")
        embeddedKafka.kafkaServers.forEach {
            it.shutdown()
        }
        clearAllMocks()
        embeddedKafka.destroy()
    }
    @TestConfiguration
    class TestConfig {
        @Value("\${" + EmbeddedKafkaBroker.SPRING_EMBEDDED_KAFKA_BROKERS + "}")
        private lateinit var brokerAddresses: String

        @Bean
        @Primary
        fun personService(): PersonService {
            return mockk(relaxed = true)
        }
//        @Bean
//        fun sedMottattListener(): SedMottattListener {
//            return mockk()
//        }
    }

    init {
        val port = randomFrom()
        println("****************************** init med post: $port ********************************")

        System.setProperty("mockserverport", port.toString())
        mockServer = ClientAndServer.startClientAndServer(port)

    }
    private fun randomFrom(from: Int = 1024, to: Int = 65535): Int {
        return Random().nextInt(to - from) + from
    }

    class CustomMockServer() {

        fun mockSTSToken() = apply {
            mockServer.`when`(
                HttpRequest.request()
                    .withMethod(HttpMethod.GET.name)
                    .withQueryStringParameter("grant_type", "client_credentials")
            )
                .respond(
                    HttpResponse.response()
                        .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/STStoken.json"))))
                )
        }

        fun medSed(bucPath: String, bucLocation: String) = apply {

            mockServer.`when`(
                HttpRequest.request()
                    .withMethod(HttpMethod.GET.name)
                    .withPath(bucPath)
            )
                .respond(
                    HttpResponse.response()
                        .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody(String(Files.readAllBytes(Paths.get(bucLocation))))
                )
        }

/*        fun medPerson(fnr: Fodselsnummer, mockPersonlocation: String) = apply {

            mockServer.`when`(
                HttpRequest.request()
                    .withMethod(HttpMethod.GET.name)
                    .withPath(bucPath)
            )
                .respond(
                    HttpResponse.response()
                        .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                        .withStatusCode(HttpStatusCode.OK_200.code())
                        .withBody(String(Files.readAllBytes(Paths.get(bucLocation))))
                )
        }*/
    }
}