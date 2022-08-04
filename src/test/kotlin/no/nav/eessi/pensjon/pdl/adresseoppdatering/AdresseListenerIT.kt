package no.nav.eessi.pensjon.pdl.adresseoppdatering

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.listeners.SedListener
import no.nav.eessi.pensjon.models.SedHendelseModel
import no.nav.eessi.pensjon.pdl.integrationtest.CustomMockServer
import no.nav.eessi.pensjon.pdl.integrationtest.IntegrationBase
import no.nav.eessi.pensjon.pdl.integrationtest.KafkaTestConfig
import no.nav.eessi.pensjon.pdl.integrationtest.PDL_PRODUSENT_TOPIC_MOTTATT
import no.nav.eessi.pensjon.personoppslag.pdl.PersonMock
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockserver.client.MockServerClient
import org.mockserver.integration.ClientAndServer
import org.mockserver.socket.PortFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.util.concurrent.TimeUnit


@SpringBootTest( classes = [KafkaTestConfig::class, IntegrationBase.TestConfig::class])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(
    controlledShutdown = true,
    topics = ["eessi-basis-sedMottatt-v1"]
)
internal class AdresseListenerIT : IntegrationBase(){
    val fnr = "11067122781"

    @Autowired
    lateinit var adresseListener: AdresseListener

    val mockedPerson = PersonMock.createWith(
        fnr = fnr,
        aktoerId = AktoerId("1231231231"),
        uid = emptyList()
    )

    @Test
    fun `Gitt en sed hendelse som kommer på riktig topic og group_id så skal den konsumeres av adresseListener`() {
        //given
        val mockSedHendelse : SedHendelseModel = mockk(relaxed = true)
        //when
        kafkaTemplate.sendDefault(mockSedHendelse.toJson())
        //then
        adresseListener.latch.await(5, TimeUnit.SECONDS)
        assertEquals(0, adresseListener.latch.count)
    }

    @Test
    fun `Gitt en sed der adresse allerede finnes i PDL, og adresse er lik så skal ikke PDL oppdateres`() {

        //given
        val listOverSeder = listOf(mockForenkletSed("eb938171a4cb4e658b3a6c011962d204", SedType.P2100, SedStatus.RECEIVED))
        val mockBuc = mockBuc("147729", BucType.P_BUC_10, listOverSeder)
        CustomMockServer()
            .medSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d204", "src/test/resources/eux/sed/P2100-PinDK-NAV.json")
            .medMockBuc("/buc/147729", mockBuc)

        every { personService.hentPerson(NorskIdent( "11067122781")) } returns mockedPerson

        //when
        kafkaTemplate.sendDefault(javaClass.getResource("/eux/hendelser/P_BUC_01_P2000.json").readText())

        //then
        adresseListener.latch.await(20, TimeUnit.SECONDS)
        assertEquals(0, adresseListener.latch.count)

        //TODO: sjekke at adresse er lik og at det skjer en oppdatering med ny timestamp (dobbeltsjekk dette)
    }







}