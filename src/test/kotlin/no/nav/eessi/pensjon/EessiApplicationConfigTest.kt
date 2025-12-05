package no.nav.eessi.pensjon

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.MockkBeans
import no.nav.eessi.pensjon.config.RestTemplateConfig
import no.nav.eessi.pensjon.eux.klient.EuxKlientAsSystemUser
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.klienter.norg2.Norg2Klient
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import no.nav.eessi.pensjon.personoppslag.pdl.PersonClient
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.RestTemplate

@SpringBootTest(classes = [RestTemplateConfig::class, EessiApplicationConfigTest.KafkaConfig::class, UnsecuredWebMvcTestLauncher::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(profiles = [ "excludeKodeverk","unsecured-webmvctest", "integrationtest"])
@EmbeddedKafka
@EnableMockOAuth2Server
@AutoConfigureMockMvc
@MockkBeans(
    //rest
    MockkBean(name = "prefillOAuthTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "euxSystemRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "safRestOidcRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "pdlRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "euxNavIdentRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "restEuxTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "safGraphQlOidcRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "kodeverkRestTemplate", classes = [RestTemplate::class]),
    //service / clients
    MockkBean(name = "personService", classes = [PersonService::class]),
    MockkBean(name = "euxKlient", classes = [EuxKlientAsSystemUser::class]),
    MockkBean(name = "gcpStorageService", classes = [GcpStorageService::class]),
    MockkBean(name = "norg2Klient", classes = [Norg2Klient::class]),
    MockkBean(name = "personMottakKlient", classes = [PersonMottakKlient::class]),
    MockkBean(name = "kodeverkClient", classes = [KodeverkClient::class]),
    MockkBean(name = "personClient", classes = [PersonClient::class]),
    //kafka
    MockkBean(name = "sedKafkaListenerContainerFactory", classes = [ConcurrentKafkaListenerContainerFactory::class], relaxed = true),

)
class EessiApplicationConfigTest {

    @Test
    fun `contextTest`(){
        println("alt er vel om vi kommer hit")
    }

    @TestConfiguration
    class KafkaConfig {
        @Bean
        fun producerFactory(): ProducerFactory<String, String> {
            val configProps: MutableMap<String, Any> = HashMap<String, Any>()
            configProps[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = "localhost:9092"
            configProps[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
            configProps[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
            return DefaultKafkaProducerFactory(configProps)
        }

        @Bean
        fun kafkaTemplate(): KafkaTemplate<String, String> {
            return KafkaTemplate(producerFactory())
        }
    }
}
