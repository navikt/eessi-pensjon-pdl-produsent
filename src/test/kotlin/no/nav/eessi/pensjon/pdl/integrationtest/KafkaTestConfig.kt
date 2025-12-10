//package no.nav.eessi.pensjon.pdl.integrationtest
//
//import io.mockk.mockk
//import no.nav.eessi.pensjon.gcp.GcpStorageService
//import no.nav.eessi.pensjon.personoppslag.pdl.PdlToken
//import no.nav.eessi.pensjon.personoppslag.pdl.PdlTokenCallBack
//import no.nav.eessi.pensjon.personoppslag.pdl.PdlTokenImp
//import org.apache.kafka.clients.CommonClientConfigs
//import org.apache.kafka.clients.consumer.ConsumerConfig
//import org.apache.kafka.clients.producer.ProducerConfig
//import org.apache.kafka.common.config.SslConfigs
//import org.apache.kafka.common.serialization.StringDeserializer
//import org.apache.kafka.common.serialization.StringSerializer
//import org.springframework.beans.factory.annotation.Autowired
//import org.springframework.beans.factory.annotation.Value
//import org.springframework.boot.restclient.RestTemplateBuilder
//import org.springframework.boot.test.context.TestConfiguration
//import org.springframework.context.annotation.Bean
//import org.springframework.context.annotation.Configuration
//import org.springframework.context.annotation.Primary
//import org.springframework.context.annotation.Profile
//import org.springframework.core.Ordered
//import org.springframework.core.annotation.Order
//import org.springframework.kafka.annotation.EnableKafka
//import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
//import org.springframework.kafka.core.ConsumerFactory
//import org.springframework.kafka.core.DefaultKafkaConsumerFactory
//import org.springframework.kafka.core.DefaultKafkaProducerFactory
//import org.springframework.kafka.core.KafkaTemplate
//import org.springframework.kafka.core.ProducerFactory
//import org.springframework.kafka.listener.ContainerProperties
//import org.springframework.web.client.RestTemplate
//import java.time.Duration
//
//@TestConfiguration
//class KafkaTestConfig(
//    @param:Value("\${kafka.keystore.path}") private val keystorePath: String,
//    @param:Value("\${kafka.credstore.password}") private val credstorePassword: String,
//    @param:Value("\${kafka.truststore.path}") private val truststorePath: String,
//    @param:Value("\${kafka.brokers}") private val bootstrapServers: String,
//    @param:Value("\${kafka.security.protocol}") private val securityProtocol: String,
//    @Value("\${KAFKA_OPPGAVE_TOPIC}") private val oppgaveTopic: String,
//) {
//
//    @Bean
//    fun producerFactory(): ProducerFactory<String, String> {
//        val configMap: MutableMap<String, Any> = HashMap()
//        populerCommonConfig(configMap)
//        configMap[ProducerConfig.CLIENT_ID_CONFIG] = "eessi-pensjon-pdl-produsent"
//        configMap[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
//        configMap[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
//        configMap[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
//
//        return DefaultKafkaProducerFactory(configMap)
//    }
//
//    @Bean
//    fun oppgaveKafkaTemplate(): KafkaTemplate<String, String> {
//        return KafkaTemplate(producerFactory()).apply {
//            setDefaultTopic(oppgaveTopic)
//        }
//    }
//
//    @Bean
//    fun kafkaConsumerFactory(): ConsumerFactory<String, String> {
//        val configMap: MutableMap<String, Any> = HashMap()
//        populerCommonConfig(configMap)
//        configMap[ConsumerConfig.CLIENT_ID_CONFIG] = "eessi-pensjon-pdl-produsent"
//        configMap[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
//        configMap[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = false
//        configMap[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
//        configMap[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 1
//        //return DefaultKafkaConsumerFactory(configMap)
//        return DefaultKafkaConsumerFactory(configMap, StringDeserializer(), StringDeserializer())
//
//    }
//
//    @Bean
//    fun sedKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, String>? {
//        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
//        factory.setConsumerFactory(kafkaConsumerFactory())
//        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
//        factory.containerProperties.setAuthExceptionRetryInterval(Duration.ofSeconds(4L))
//        return factory
//    }
//
//    @Bean
//    fun kafkaTemplate(): KafkaTemplate<String, String> {
//        return KafkaTemplate(producerFactory())
//    }
//
//    @Bean
//    fun personMottakRestTemplate(): RestTemplate {
//        return mockedRestTemplate()
//    }
//
//    @Bean
//    fun kodeverkRestTemplate(): RestTemplate {
//        return mockk()
//    }
//
//    @Bean
//    fun norg2OidcRestTemplate(): RestTemplate {
//        return mockk()
//    }
//
//    private fun mockedRestTemplate(): RestTemplate {
//        val port = System.getProperty("mockserverport")
//        return RestTemplateBuilder()
//            .rootUri("http://localhost:${port}")
//            .build()
//    }
//
//    @Bean
//    fun kafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, String>? {
//        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
//        factory.setConsumerFactory(kafkaConsumerFactory())
//        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
//        factory.containerProperties.setAuthExceptionRetryInterval(Duration.ofSeconds(4L))
//        return factory
//    }
//
//    private fun populerCommonConfig(configMap: MutableMap<String, Any>) {
//        configMap[SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG] = keystorePath
//        configMap[SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG] = credstorePassword
//        configMap[SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG] = credstorePassword
//        configMap[SslConfigs.SSL_KEY_PASSWORD_CONFIG] = credstorePassword
//        configMap[SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG] = "JKS"
//        configMap[SslConfigs.SSL_KEYSTORE_TYPE_CONFIG] = "PKCS12"
//        configMap[SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG] = truststorePath
//        configMap[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = securityProtocol
////        configMap[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = "PLAINTEXT"
//    }
//
//    @Bean("pdlTokenComponent")
//    @Primary
//    @Order(Ordered.HIGHEST_PRECEDENCE)
//    fun pdlTokenComponent(): PdlTokenCallBack {
//        return object : PdlTokenCallBack {
//            override fun callBack(): PdlToken {
//                return PdlTokenImp("Dummytoken")
//            }
//        }
//    }
//
//    @Bean
//    fun gcpStorageService(): GcpStorageService {
//        return mockk()
//    }
//}