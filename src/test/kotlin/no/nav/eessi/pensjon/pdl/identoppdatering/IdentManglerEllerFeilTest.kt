package no.nav.eessi.pensjon.pdl.identoppdatering

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.pdl.identoppdatering.IdentOppdatering.NoUpdate
import no.nav.eessi.pensjon.pdl.integrationtest.CustomMockServer
import no.nav.eessi.pensjon.pdl.integrationtest.IntegrationBase
import no.nav.eessi.pensjon.pdl.integrationtest.KafkaTestConfig
import no.nav.eessi.pensjon.pdl.integrationtest.PDL_PRODUSENT_TOPIC_MOTTATT
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.PersonMock
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.util.concurrent.*

@SpringBootTest( classes = [KafkaTestConfig::class, IntegrationBase.TestConfig::class])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(
    controlledShutdown = true,
    topics = [PDL_PRODUSENT_TOPIC_MOTTATT]
)
class IdentManglerEllerFeilTest : IntegrationBase() {

    private val acknowledgment = mockk<Acknowledgment>(relaxUnitFun = true)
    private val cr = mockk<ConsumerRecord<String, String>>(relaxed = true)

    @Autowired
    lateinit var sedListenerIdent: SedListenerIdent

    val fnr = "11067122781"

    val mockedPerson = PersonMock.createWith(
        fnr = fnr,
        aktoerId = AktoerId("1231231231"),
        uid = emptyList()
    )

    /* overstyrer for å droppe Kafka i denne testen */
    override fun sendMeldingString(message: String) {
        sedListenerIdent.consumeSedMottatt(message, cr, acknowledgment)
    }

    @Test
    fun `Gitt en sed hendelse med uten dansk uid som ikke finnes i pdl skal det ack med logg Ingen utenlandske IDer funnet i BUC`() {
        every { norg2.hentArbeidsfordelingEnhet(any()) } returns Enhet.ID_OG_FORDELING
        every { personService.hentPerson(NorskIdent( fnr)) } returns mockedPerson

        val listOverSeder = listOf(ForenkletSED("eb938171a4cb4e658b3a6c011962d204", SedType.P15000, SedStatus.RECEIVED))
        val mockBuc = CustomMockServer.mockBuc("147729", BucType.P_BUC_10, listOverSeder)

        val mockPin = mockPin(fnr, "NO")
        val mockSed = mockSedUtenPensjon(sedType = SedType.P15000, pin = listOf(mockPin))

        CustomMockServer()
            .medMockSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d204", mockSed)
            .medMockBuc("/buc/147729", mockBuc)
            .medKodeverk("/api/v1/hierarki/LandkoderSammensattISO2/noder", "src/test/resources/kodeverk/landkoderSammensattIso2.json")

        sendMeldingString(
            mockHendelse(
                bucType = BucType.P_BUC_10,
                sedType = SedType.P15000,
                docId = "eb938171a4cb4e658b3a6c011962d204"
            )
        )
        sedListenerIdent.getLatch().await(20, TimeUnit.SECONDS)

        assertTrue(isMessageInlog("Ingen utenlandske IDer funnet i BUC"))
    }

    @Test
    fun `Gitt en sed-hendelse fra Sverige som sender inn en tysk uid så skal det stoppes av valideringen`() {
        every { norg2.hentArbeidsfordelingEnhet(any()) } returns  Enhet.ID_OG_FORDELING
        every { personService.hentPerson(NorskIdent( "29087021082")) } returns mockedPerson

        val listOverSeder = listOf(ForenkletSED("eb938171a4cb4e658b3a6c011962d204", SedType.P8000, SedStatus.RECEIVED))
        val mockBuc = CustomMockServer.mockBuc("147729", BucType.P_BUC_02, listOverSeder)

        CustomMockServer()
            .medSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d204", "src/test/resources/eux/sed/P8000-TyskPIN.json")
            .medMockBuc("/buc/147729", mockBuc)
            .medKodeverk("/api/v1/hierarki/LandkoderSammensattISO2/noder", "src/test/resources/kodeverk/landkoderSammensattIso2.json")

        sendMeldingString(javaClass.getResource("/eux/hendelser/P_BUC_01_P2000-avsenderSE.json").readText())
        sedListenerIdent.getLatch().await(20, TimeUnit.SECONDS)

        assertTrue(isMessageInlog("Avsenderland mangler eller avsenderland er ikke det samme som uidland, stopper identifisering av personer"))
        assertThat(NoUpdate("Avsenderland mangler eller avsenderland er ikke det samme som uidland, stopper identifisering av personer"))

        CustomMockServer().verifyRequest("/api/v1/endringer", 0)
    }

    @Test
    fun `Gitt en sed hendelse uten ident ack med logg Ingen utenlandske IDer funnet i BUC`() {
        every { norg2.hentArbeidsfordelingEnhet(any()) } returns Enhet.ID_OG_FORDELING

        val listOverSeder = listOf(ForenkletSED("eb938171a4cb4e658b3a6c011962d204", SedType.P15000, SedStatus.RECEIVED))
        val mockBuc = CustomMockServer.mockBuc("147729", BucType.P_BUC_10, listOverSeder)

        CustomMockServer()
            .medSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d204", "src/test/resources/eux/sed/P15000-UtenPin-NAV.json")
            .medMockBuc("/buc/147729", mockBuc)
            .medKodeverk("/api/v1/hierarki/LandkoderSammensattISO2/noder", "src/test/resources/kodeverk/landkoderSammensattIso2.json")


        sendMeldingString(
            mockHendelse(
                bucType = BucType.P_BUC_10,
                sedType = SedType.P15000,
                docId = "eb938171a4cb4e658b3a6c011962d204"
            )
        )
        sedListenerIdent.getLatch().await(20, TimeUnit.SECONDS)

        assertTrue(isMessageInlog("Ingen utenlandske IDer funnet i BUC"))
    }

    @Test
    fun `Gitt en sed-hendelse fra Tyskland med flere uid i sed så skal det stoppes av valideringen`() {
        every { norg2.hentArbeidsfordelingEnhet(any()) } returns Enhet.ID_OG_FORDELING
        every { personService.hentPerson(NorskIdent( "29087021082")) } returns mockedPerson

        val listOverSeder = listOf(ForenkletSED("eb938171a4cb4e658b3a6c011962d204", SedType.P8000, SedStatus.RECEIVED))
        val mockBuc = CustomMockServer.mockBuc("147729", BucType.P_BUC_02, listOverSeder)

        CustomMockServer()
            .medSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d204", "src/test/resources/eux/sed/P8000-TyskOgFinskPIN.json")
            .medMockBuc("/buc/147729", mockBuc)
            .medKodeverk("/api/v1/hierarki/LandkoderSammensattISO2/noder", "src/test/resources/kodeverk/landkoderSammensattIso2.json")

        sendMeldingString(javaClass.getResource("/eux/hendelser/P_BUC_01_P2000-avsenderDE.json").readText())
        sedListenerIdent.getLatch().await(20, TimeUnit.SECONDS)

        assertTrue(isMessageInlog("Antall utenlandske IDer er flere enn en"))

        CustomMockServer().verifyRequest("/api/v1/endringer", 0)
    }
}

