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
import no.nav.eessi.pensjon.listeners.SedListener
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.security.sts.STSService
import no.nav.eessi.pensjon.utils.toJson
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockserver.integration.ClientAndServer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.KafkaMessageListenerContainer
import org.springframework.kafka.listener.MessageListener
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.utils.ContainerTestUtils
import org.springframework.kafka.test.utils.KafkaTestUtils
import java.util.*
import java.util.concurrent.TimeUnit

const val PDL_PRODUSENT_TOPIC_MOTATT = "eessi-basis-sedMottatt-v1"
const val PDL_PRODUSENT_TOPIC_SENDT = "eessi-basis-sedSendt-v1"

abstract class IntegrationBase {

    @Autowired(required = true)
    lateinit var sedListener: SedListener

    @Autowired
    lateinit var embeddedKafka: EmbeddedKafkaBroker

    @Autowired
    lateinit var producerFactory: ProducerFactory<String, String>

    @MockkBean
    lateinit var stsService: STSService

    @MockkBean
    lateinit var personService: PersonService

    private val deugLogger: Logger = LoggerFactory.getLogger("no.nav.eessi.pensjon") as Logger
    private val listAppender = ListAppender<ILoggingEvent>()

    var mockServer: ClientAndServer

    init {
        randomFrom().apply {
            System.setProperty("mockserverport", "" + this)
            mockServer = ClientAndServer.startClientAndServer(this)
        }
    }

    @BeforeEach
    fun setup() {
        every { stsService.getSystemOidcToken() } returns "a nice little token?"
        listAppender.start()
        deugLogger.addAppender(listAppender)
    }

    @AfterEach
    fun after() {
        println("****************************** after ********************************")
        listAppender.stop()
        embeddedKafka.kafkaServers.forEach {
            it.shutdown()
        }
        embeddedKafka.destroy()
        mockServer.stop().also { print("mockServer -> HasStopped: ${mockServer.hasStopped()}") }
    }

    fun initAndRunContainer(topic: String): TestResult {
        val container = initConsumer(topic)
        container.start()

        ContainerTestUtils.waitForAssignment(container, embeddedKafka.partitionsPerTopic)
        var template = KafkaTemplate(producerFactory).apply { defaultTopic = topic }
        return TestResult(template, container).also {
            println("*************************  INIT DONE *****************************")
        }
    }
    data class TestResult(
        val kafkaTemplate: KafkaTemplate<String, String>,
        val topic: KafkaMessageListenerContainer<String, String>
    ) {

        fun sendMsgOnDefaultTopic(kafkaMsgFromPath: String) {
            kafkaTemplate.sendDefault(kafkaMsgFromPath)
        }

        fun waitForlatch(listener: SedListener) {
            listener.getLatch().await(30, TimeUnit.SECONDS)
            listener.getSendtLatch().await(30, TimeUnit.SECONDS)
        }
    }

    private fun initConsumer(topicName: String): KafkaMessageListenerContainer<String, String> {
        val consumerProperties = KafkaTestUtils.consumerProps(
            UUID.randomUUID().toString(),
            "false",
            embeddedKafka
        )
        consumerProperties[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        val consumerFactory = DefaultKafkaConsumerFactory<String, String>(consumerProperties)

        return KafkaMessageListenerContainer(consumerFactory, ContainerProperties(topicName)).apply {
            setupMessageListener(MessageListener<String, String> { record -> println("Konsumerer melding:  $record") })
        }
    }

    private fun randomFrom(from: Int = 1024, to: Int = 65535): Int {
        return Random().nextInt(to - from) + from
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

    fun mockForenkletSed(id: String, type: SedType, status: SedStatus) : ForenkletSED {
        return ForenkletSED(id, type, status)
    }

    fun mockHendlese(avsenderLand: String = "DK", avsenderNavn: String = "DK:D005", bucType: BucType = BucType.P_BUC_01, sedType: SedType = SedType.P2000, docId: String = "b12e06dda2c7474b9998c7139c841646"): String {
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

    fun mockPin(ident: String = "12312312312", land: String = "NO") : String {
        return """
            {
          "institusjonsnavn" : "NOINST002, NO INST002, NO",
          "institusjonsid" : "NO:noinst002",
          "identifikator" : "$ident",
          "land" : "$land"
        }
        """.trimIndent()
    }

    fun mockSedUtenPensjon(sedType: SedType, pin: String, fornavn: String = "Fornavn", krav: String = "01"): String {
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
                    "pin" : [ $pin ],
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

}