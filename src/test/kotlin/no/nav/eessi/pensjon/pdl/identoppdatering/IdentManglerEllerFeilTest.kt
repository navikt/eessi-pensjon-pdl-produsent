package no.nav.eessi.pensjon.pdl.identoppdatering

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.pdl.integrationtest.CustomMockServer
import no.nav.eessi.pensjon.pdl.integrationtest.IntegrationBase
import no.nav.eessi.pensjon.pdl.integrationtest.KafkaTestConfig
import no.nav.eessi.pensjon.pdl.integrationtest.PDL_PRODUSENT_TOPIC_MOTTATT
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.PersonMock
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.util.concurrent.TimeUnit

@SpringBootTest( classes = [KafkaTestConfig::class, IntegrationBase.TestConfig::class])
@ActiveProfiles("integrationtest", "excludeKodeverk")
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

    @MockkBean
    lateinit var kodeverkClient: KodeverkClient

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
    fun `En sed hendelse uten UID vil resultere i en NoUpdate`() {
        every { norg2.hentArbeidsfordelingEnhet(any()) } returns Enhet.ID_OG_FORDELING
        every { personService.hentPerson(NorskIdent( fnr)) } returns mockedPerson

        val listOverSeder = listOf(ForenkletSED("eb938171a4cb4e658b3a6c011962d204", SedType.P15000, SedStatus.RECEIVED))
        val mockBuc = CustomMockServer.mockBuc("147729", BucType.P_BUC_10, listOverSeder)

        val mockNorskPin = mockPin(fnr, "NO")
        val mockSed = mockSedUtenPensjon(sedType = SedType.P15000, pin = listOf(mockNorskPin))

        CustomMockServer()
            .medMockSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d204", mockSed)
            .medMockBuc("/buc/147729", mockBuc)
            .medEndring()

        sendMeldingString(
            mockHendelse(
                bucType = BucType.P_BUC_10,
                sedType = SedType.P15000,
                avsenderLand = "NO",
                docId = "eb938171a4cb4e658b3a6c011962d204"
            )
        )
        sedListenerIdent.getLatch().await(20, TimeUnit.SECONDS)

        assertTrue(isMessageInlog("NoUpdate(description=Bruker har ikke utenlandsk ident)"))
    }

    @Test
    fun `Gitt en sed-hendelse fra Sverige som sender inn en tysk uid saa skal det stoppes av valideringen`() {
        every { norg2.hentArbeidsfordelingEnhet(any()) } returns  Enhet.ID_OG_FORDELING
        every { personService.hentPerson(NorskIdent( "29087021082")) } returns mockedPerson

        val listOverSeder = listOf(ForenkletSED("b12e06dda2c7474b9998c7139c841646", SedType.P8000, SedStatus.RECEIVED))
        val mockBuc = CustomMockServer.mockBuc("147729", BucType.P_BUC_02, listOverSeder)

        CustomMockServer()
            .medSed("/buc/147729/sed/b12e06dda2c7474b9998c7139c841646", "src/test/resources/eux/sed/P8000-TyskPIN.json")
            .medMockBuc("/buc/147729", mockBuc)

        sendMeldingString(javaClass.getResource("/eux/hendelser/P_BUC_01_P2000-avsenderSE.json")!!.readText())
        sedListenerIdent.getLatch().await(20, TimeUnit.SECONDS)

        assertTrue(isMessageInlog("Avsenderland er ikke det samme som uidland"))

        //sjekker at vi ikke har en ident oppdatering
        CustomMockServer().verifyRequest("/api/v1/endringer", 0)
    }

    @Test
    fun `Gitt en sed hendelse uten avsenderNavn saa sender vi ingen oppdatering`() {
        every { norg2.hentArbeidsfordelingEnhet(any()) } returns Enhet.ID_OG_FORDELING
        every { personService.hentPerson(NorskIdent( "29087021082")) } returns mockedPerson

        val listOverSeder = listOf(ForenkletSED("eb938171a4cb4e658b3a6c011962d204", SedType.P15000, SedStatus.RECEIVED))
        val mockBuc = CustomMockServer.mockBuc("147729", BucType.P_BUC_10, listOverSeder)

        CustomMockServer()
            .medSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d204", "src/test/resources/eux/sed/P2100-PinDK-NAV.json")
            .medMockBuc("/buc/147729", mockBuc)


        sendMeldingString(
            mockHendelse(
                bucType = BucType.P_BUC_10,
                sedType = SedType.P15000,
                avsenderLand = "DK",
                avsenderNavn = "",
                docId = "eb938171a4cb4e658b3a6c011962d204"
            )
        )
        sedListenerIdent.getLatch().await(20, TimeUnit.SECONDS)

        assertTrue(isMessageInlog("AvsenderNavn er ikke satt, kan derfor ikke lage endringsmelding"))
    }
}

