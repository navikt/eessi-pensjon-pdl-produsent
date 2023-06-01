package no.nav.eessi.pensjon

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.MockkBeans
import no.nav.eessi.pensjon.config.RestTemplateConfig
import no.nav.eessi.pensjon.eux.klient.EuxKlientAsSystemUser
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.klienter.norg2.Norg2Klient
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import no.nav.eessi.pensjon.pdl.identOppdateringGjenlev.SedHendelseGjenlevIdentBehandler
import no.nav.eessi.pensjon.pdl.identOppdateringGjenlev.VurderGjenlevOppdateringIdent
import no.nav.eessi.pensjon.pensjonsinformasjon.clients.PensjonsinformasjonClient
import no.nav.eessi.pensjon.personoppslag.pdl.PersonClient
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.RestTemplate

@SpringBootTest(classes = [RestTemplateConfig::class, UnsecuredWebMvcTestLauncher::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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
    MockkBean(classes = [PersonService::class]),
    MockkBean(classes = [EuxKlientAsSystemUser::class]),
    MockkBean(classes = [GcpStorageService::class]),
    MockkBean(classes = [Norg2Klient::class]),
    MockkBean(classes = [PersonMottakKlient::class]),
    MockkBean(classes = [KodeverkClient::class]),
    MockkBean(classes = [PersonClient::class]),
    MockkBean(classes = [SedHendelseGjenlevIdentBehandler::class]),
    MockkBean(classes = [VurderGjenlevOppdateringIdent::class]),
    MockkBean(classes = [PensjonsinformasjonClient::class]),
    //kafka
    MockkBean(name = "sedKafkaListenerContainerFactory", classes = [ConcurrentKafkaListenerContainerFactory::class], relaxed = true),

)
class EessiApplicationConfigTest {

    @Test
    fun `contextTest`(){
        println("alt er vel om vi kommer hit")
    }
}
