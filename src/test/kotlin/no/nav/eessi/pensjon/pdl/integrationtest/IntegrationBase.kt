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
import no.nav.eessi.pensjon.listeners.SedMottattListener
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.security.sts.STSService
import no.nav.eessi.pensjon.utils.toJson
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

const val PDL_PRODUSENT_TOPIC_MOTATT = "eessi-basis-sedmottatt-v1"

abstract class IntegrationBase {

    @Autowired(required = true)
    lateinit var sedMottattListener: SedMottattListener

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

        fun waitForlatch(listener: SedMottattListener) = listener.getLatch().await(10, TimeUnit.SECONDS)
    }

    private fun initConsumer(topicName: String): KafkaMessageListenerContainer<String, String> {
        val consumerProperties = KafkaTestUtils.consumerProps(
            "eessi-pensjon-group",
            "false",
            embeddedKafka
        )
        consumerProperties["auto.offset.reset"] = "earliest"
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

}