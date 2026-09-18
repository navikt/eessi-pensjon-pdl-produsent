package no.nav.eessi.pensjon.pdl.identoppdateringgjenlev

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.klienter.saf.SafClient
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.lagring.LagringsService
import no.nav.eessi.pensjon.oppgave.OppgaveOppslag
import no.nav.eessi.pensjon.pdl.integrationtest.IntegrationBase
import no.nav.eessi.pensjon.pdl.integrationtest.KafkaTestConfig
import no.nav.eessi.pensjon.pdl.integrationtest.PDL_PRODUSENT_TOPIC_MOTTATT
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.RestTemplate

@SpringBootTest( classes = [KafkaTestConfig::class, IntegrationBase.TestConfig::class])
@ActiveProfiles("integrationtest", "excludeKodeverk")
@DirtiesContext
@EmbeddedKafka(
    controlledShutdown = true,
    topics = [PDL_PRODUSENT_TOPIC_MOTTATT]
)
@MockkBean(name = "euxService", types = [EuxService::class], relaxed = true)
@MockkBean(name = "safClient", types = [SafClient::class], relaxed = true)
@MockkBean(name = "kodeverkClient", types = [KodeverkClient::class], relaxed = true)
@MockkBean(name = "oppgaveOppslag", types = [OppgaveOppslag::class])
@MockkBean(name = "personService", types = [PersonService::class])
@MockkBean(name = "pdlRestTemplate", types = [RestTemplate::class])
@MockkBean(name = "safGraphQlOidcRestTemplate", types = [RestTemplate::class])
class SedHendelseGjenlevIdentBehandlerTest : IntegrationBase(){

    @MockkBean(relaxed = true)
    lateinit var kodeverkClient: KodeverkClient

    @MockkBean
    lateinit var lagringsService: LagringsService

    @Autowired
    lateinit var identoppdatering: VurderGjenlevOppdateringIdent
    @Autowired
    lateinit var gjenlevIdentBehandler: SedHendelseGjenlevIdentBehandler

    @Test
    fun configTest(){
        every { lagringsService.kanHendelsenOpprettes(any()) } returns false
    }
}