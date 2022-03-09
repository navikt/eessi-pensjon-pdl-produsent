package no.nav.eessi.pensjon.pdl.integrationtest

import io.mockk.mockk
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.personoppslag.pdl.PdlToken
import no.nav.eessi.pensjon.personoppslag.pdl.PdlTokenCallBack
import no.nav.eessi.pensjon.personoppslag.pdl.PdlTokenImp
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.web.client.RestTemplate
import java.time.Duration

@TestConfiguration
class KafkaTestConfig(
    @param:Value("\${spring.embedded.kafka.brokers}") private val bootstrapServers: String,
    @Value("\${KAFKA_OPPGAVE_TOPIC}") private val oppgaveTopic: String) {

    @Bean
    fun aivenProducerFactory(): ProducerFactory<String, String> {
        val configMap: MutableMap<String, Any> = HashMap()
        populerCommonConfig(configMap)
        configMap[ProducerConfig.CLIENT_ID_CONFIG] = "eessi-pensjon-pdl-produsent"
        configMap[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        configMap[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        configMap[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers

        return DefaultKafkaProducerFactory(configMap)
    }

    @Bean("aivenOppgaveKafkaTemplate")
    fun aivenOppgaveKafkaTemplate(): KafkaTemplate<String, String> {
        val template = KafkaTemplate(aivenProducerFactory())
        template.defaultTopic = oppgaveTopic
        return template
    }

    @Bean
    fun kafkaConsumerFactory(): ConsumerFactory<String, String> {
        val configMap: MutableMap<String, Any> = HashMap()
        populerCommonConfig(configMap)
        configMap[ConsumerConfig.CLIENT_ID_CONFIG] = "eessi-pensjon-pdl-produsent"
        configMap[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        configMap[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        configMap[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
        configMap[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = false

        return DefaultKafkaConsumerFactory(configMap)
    }

    @Bean
    fun onpremKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, String>? {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.consumerFactory = kafkaConsumerFactory()
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
        factory.containerProperties.setAuthExceptionRetryInterval(Duration.ofSeconds(4L))
        return factory
    }

    @Bean
    fun aivenKafkaTemplate(): KafkaTemplate<String, String> {
        return KafkaTemplate(aivenProducerFactory())
    }

    @Bean
    fun personMottakRestTemplate(): RestTemplate {
        return mockk()
    }

    @Bean
    fun kodeverkRestTemplate(): RestTemplate {
        return mockk()
    }

    @Bean
    fun norg2OidcRestTemplate(): RestTemplate {
        return mockk()
    }

    @Bean
    fun aivenKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, String>? {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.consumerFactory = kafkaConsumerFactory()
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
        factory.containerProperties.setAuthExceptionRetryInterval(Duration.ofSeconds(4L))
        return factory
    }

    private fun populerCommonConfig(configMap: MutableMap<String, Any>) {
        configMap[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = "PLAINTEXT"
    }

    @Bean("pdlTokenComponent")
    @Primary
    @Order(Ordered.HIGHEST_PRECEDENCE)
    fun pdlTokenComponent(): PdlTokenCallBack {
        return object : PdlTokenCallBack {
            override fun callBack(): PdlToken {
                return PdlTokenImp(systemToken = "Dummytoken", userToken = "DummyToken", isUserToken = false)
            }
        }
    }

    @Bean
    fun gcpStorageService(): GcpStorageService {
        return mockk()
    }
}