package no.nav.eessi.pensjon.pdl.integrationtest

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.eux.model.sed.PinItem
import no.nav.eessi.pensjon.klienter.norg2.Norg2Service
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.typeRefs
import org.apache.http.conn.ssl.NoopHostnameVerifier
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.ssl.SSLContexts
import org.apache.http.ssl.TrustStrategy
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockserver.client.MockServerClient
import org.mockserver.integration.ClientAndServer
import org.mockserver.socket.PortFactory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.KafkaMessageListenerContainer
import org.springframework.kafka.listener.MessageListener
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.utils.ContainerTestUtils
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.web.client.RestTemplate
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext

const val PDL_PRODUSENT_TOPIC_MOTTATT = "eessi-basis-sedMottatt-v1"
abstract class IntegrationBase {

    @Autowired
    lateinit var embeddedKafka: EmbeddedKafkaBroker

    @Autowired
    lateinit var producerFactory: ProducerFactory<String, String>

    @MockkBean
    lateinit var norg2: Norg2Service

    @MockkBean
    lateinit var personService: PersonService

    private val deugLogger: Logger = LoggerFactory.getLogger("no.nav.eessi.pensjon") as Logger
    private val listAppender = ListAppender<ILoggingEvent>()

    lateinit var mockServer: ClientAndServer
    lateinit var kafkaTemplate: KafkaTemplate<String, String>
    lateinit var mottattContainer: KafkaMessageListenerContainer<String, String>

    init {
        if (System.getProperty("mockserverport") == null) {
            mockServer = ClientAndServer(PortFactory.findFreePort())
                .also {
                    System.setProperty("mockserverport", it.localPort.toString())
                }
        }
    }

    @BeforeEach
    fun setup() {
        every { personService.harAdressebeskyttelse(any(), any()) } returns false

        listAppender.start()
        deugLogger.addAppender(listAppender)
        mottattContainer = initConsumer()
        mottattContainer.start()
        ContainerTestUtils.waitForAssignment(mottattContainer, embeddedKafka.partitionsPerTopic)
        Thread.sleep(2000) // wait a bit for the container to start

        kafkaTemplate =  KafkaTemplate(producerFactory).apply { defaultTopic = PDL_PRODUSENT_TOPIC_MOTTATT }

    }

    @AfterEach
    fun after() {
        println("****************************** after ********************************")
        listAppender.stop()
        mottattContainer.stop()
        MockServerClient("localhost", System.getProperty("mockserverport").toInt()).reset()
    }

    fun sendMeldingString(message: String) {
        kafkaTemplate.sendDefault(message).get(20L, TimeUnit.SECONDS)
        Thread.sleep(5000)
    }

    private fun initConsumer(): KafkaMessageListenerContainer<String, String> {
        val consumerProperties = KafkaTestUtils.consumerProps(
            UUID.randomUUID().toString(),
            "false",
            embeddedKafka
        )
        consumerProperties[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        val consumerFactory = DefaultKafkaConsumerFactory<String, String>(consumerProperties)

        return KafkaMessageListenerContainer(consumerFactory, ContainerProperties(PDL_PRODUSENT_TOPIC_MOTTATT)).apply {
            setupMessageListener(MessageListener<String, String> { record -> println("Konsumerer melding:  $record") })
        }
    }

    fun validateSedMottattListenerLoggingMessage(keyword: String): Boolean {
        val logsList: List<ILoggingEvent> = listAppender.list
        return logsList.find { logMelding ->
            logMelding.message.contains(keyword)
        }?.message?.isNotEmpty() ?: false
    }

    fun mockBuc(bucId: String, bucType: BucType, docIder: List<ForenkletSED>) : String {
        return """
            {
              "id": "$bucId",
              "processDefinitionName": "${bucType.name}",
              "documents": ${docIder.toJson()}
              
            } 
          
        """.trimIndent()
    }

    fun mockHendelse(avsenderLand: String = "DK", avsenderNavn: String = "DK:D005", bucType: BucType = BucType.P_BUC_01, sedType: SedType = SedType.P2000, docId: String = "b12e06dda2c7474b9998c7139c841646"): String {
        return """
            {
              "id": 1869,
              "sedId": "${sedType.name}-$docId-1",
              "sektorKode": "P",
              "bucType": "${bucType.name}",
              "rinaSakId": "147729",
              "avsenderId": "NO:NAVT003",
              "avsenderNavn": "$avsenderNavn",
              "avsenderLand": "$avsenderLand",
              "mottakerId": "NO:NAVT007",
              "mottakerNavn": "NAV Test 07",
              "mottakerLand": "NO",
              "rinaDokumentId": "$docId",
              "rinaDokumentVersjon": "1",
              "sedType": "${sedType.name}",
              "navBruker": null
            }
        """.trimIndent()
    }

    fun mockPin(ident: String = "12312312312", land: String = "NO") : PinItem {
        val pinjson =  """
            {
          "institusjonsnavn" : "NOINST002, NO INST002, NO",
          "institusjonsid" : "NO:noinst002",
          "identifikator" : "$ident",
          "land" : "$land"
        }
        """.trimIndent()
        return mapJsonToAny(pinjson, typeRefs())
    }

    fun mockSedUtenPensjon(sedType: SedType, pin: List<PinItem>, fornavn: String = "Fornavn", krav: String = "01"): String {
        return """
            {
              "sed" : "${sedType.name}",
              "sedGVer" : "4",
              "sedVer" : "2",
              "nav" : {
                "eessisak" : [ {
                  "institusjonsid" : "NO:noinst002",
                  "institusjonsnavn" : "NOINST002, NO INST002, NO",
                  "saksnummer" : "22915555",
                  "land" : "NO"
                } ],
                "bruker" : {
                  "person" : {
                    "pin" : ${pin.toJson()},
                    "etternavn" : "Etternavn",
                    "fornavn" : "$fornavn",
                    "kjoenn" : "M",
                    "foedselsdato" : "1976-07-12"
                  },
                  "adresse" : {
                    "gate" : "Oppoverbakken 66",
                    "by" : "SØRUMSAND",
                    "postnummer" : "1920",
                    "land" : "NO"
                  }
                },
                "krav" : {
                  "dato" : "2020-01-01",
                  "type" : "$krav"
                }
              },
              "pensjon" : null
        }
        """.trimIndent()
    }


    @TestConfiguration
    class TestConfig {

        @Bean
        fun euxOAuthRestTemplate(): RestTemplate? {
            return opprettRestTemplate()
        }

        @Bean
        fun proxyOAuthRestTemplate(): RestTemplate? {
            return opprettRestTemplate()
        }

        @Bean
        fun opprettRestTemplate(): RestTemplate {
            val acceptingTrustStrategy = TrustStrategy { chain: Array<X509Certificate?>?, authType: String? -> true }

            val sslContext: SSLContext = SSLContexts.custom()
                .loadTrustMaterial(null, acceptingTrustStrategy)
                .build()

            val httpClient: CloseableHttpClient = HttpClients.custom()
                .setSSLContext(sslContext)
                .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                .build()

            val customRequestFactory = HttpComponentsClientHttpRequestFactory()
            customRequestFactory.httpClient = httpClient
            return RestTemplateBuilder()
                .rootUri("https://localhost:${System.getProperty("mockserverport")}")
                .build().apply {
                    requestFactory = customRequestFactory
                }
        }
    }

}