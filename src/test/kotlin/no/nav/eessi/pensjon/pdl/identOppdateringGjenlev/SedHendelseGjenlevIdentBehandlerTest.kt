package no.nav.eessi.pensjon.pdl.identOppdateringGjenlev

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.MockkBeans
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
@MockkBeans(
    MockkBean(name = "euxService", classes = [EuxService::class], relaxed = true),
    MockkBean(name = "safClient", classes = [SafClient::class], relaxed = true),
    MockkBean(name = "kodeverkClient", classes = [KodeverkClient::class], relaxed = true),
    MockkBean(name = "oppgaveOppslag", classes = [OppgaveOppslag::class]),
    MockkBean(name = "personService", classes = [PersonService::class]),
    MockkBean(name = "pdlRestTemplate", classes = [RestTemplate::class]),
    MockkBean(name = "safGraphQlOidcRestTemplate", classes = [RestTemplate::class]),
)
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