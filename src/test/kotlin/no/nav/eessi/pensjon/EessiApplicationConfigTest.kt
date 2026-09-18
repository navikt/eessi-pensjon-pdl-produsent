package no.nav.eessi.pensjon

import com.ninjasquad.springmockk.MockkBean
import no.nav.eessi.pensjon.config.RestTemplateConfig
import no.nav.eessi.pensjon.eux.klient.EuxKlientAsSystemUser
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.klienter.norg2.Norg2Klient
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import no.nav.eessi.pensjon.personoppslag.pdl.PersonClient
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.RestTemplate

@SpringBootTest(classes = [RestTemplateConfig::class, EessiApplicationConfigTest.KafkaConfig::class, UnsecuredWebMvcTestLauncher::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(profiles = [ "excludeKodeverk","unsecured-webmvctest", "integrationtest"])
@DirtiesContext
@EmbeddedKafka
@EnableMockOAuth2Server
@AutoConfigureMockMvc
@MockkBean(name = "prefillOAuthTemplate", types = [RestTemplate::class])
@MockkBean(name = "euxSystemRestTemplate", types = [RestTemplate::class])
@MockkBean(name = "safRestOidcRestTemplate", types = [RestTemplate::class])
@MockkBean(name = "pdlRestTemplate", types = [RestTemplate::class])
@MockkBean(name = "euxNavIdentRestTemplate", types = [RestTemplate::class])
@MockkBean(name = "restEuxTemplate", types = [RestTemplate::class])
@MockkBean(name = "safGraphQlOidcRestTemplate", types = [RestTemplate::class])
@MockkBean(name = "kodeverkRestTemplate", types = [RestTemplate::class])
@MockkBean(name = "personService", types = [PersonService::class])
@MockkBean(name = "euxKlient", types = [EuxKlientAsSystemUser::class])
@MockkBean(name = "gcpStorageService", types = [GcpStorageService::class])
@MockkBean(name = "norg2Klient", types = [Norg2Klient::class])
@MockkBean(name = "personMottakKlient", types = [PersonMottakKlient::class])
@MockkBean(name = "kodeverkClient", types = [KodeverkClient::class])
@MockkBean(name = "personClient", types = [PersonClient::class])
@MockkBean(name = "sedKafkaListenerContainerFactory", types = [ConcurrentKafkaListenerContainerFactory::class], relaxed = true)
@MockkBean(name = "clientConfigurationProperties", types = [ClientConfigurationProperties::class])
@MockkBean(name = "oAuth2AccessTokenService", types = [OAuth2AccessTokenService::class])
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
