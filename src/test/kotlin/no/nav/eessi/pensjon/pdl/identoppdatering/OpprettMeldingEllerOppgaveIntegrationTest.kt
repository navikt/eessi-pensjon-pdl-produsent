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
import java.util.concurrent.*

@SpringBootTest( classes = [KafkaTestConfig::class, IntegrationBase.TestConfig::class])
@ActiveProfiles("integrationtest", "excludeKodeverk")
@DirtiesContext
@EmbeddedKafka(
    controlledShutdown = true,
    topics = [PDL_PRODUSENT_TOPIC_MOTTATT]
)
class OpprettMeldingEllerOppgaveIntegrationTest : IntegrationBase() {

    private val acknowledgment = mockk<Acknowledgment>(relaxUnitFun = true)
    private val cr = mockk<ConsumerRecord<String, String>>(relaxed = true)

    @Autowired
    lateinit var sedListenerIdent: SedListenerIdent

    @MockkBean
    lateinit var kodeverkClient: KodeverkClient

    val fnr = "11067122781"

    /* overstyrer for å droppe Kafka i denne testen */
    override fun sendMeldingString(message: String) {
        sedListenerIdent.consumeSedMottatt(message, cr, acknowledgment)
    }

    @Test
    fun `Gitt en hendelse med flere sed i buc og en dansk uid som ikke finnes i pdl skal det opprettes det en endringsmelding til person-mottak`() {
        every { norg2.hentArbeidsfordelingEnhet(any()) } returns Enhet.NFP_UTLAND_OSLO
        every { personService.hentPerson(NorskIdent(fnr))} returns PersonMock.createWith(
            fnr = fnr,
            uid = emptyList()
        )
        every { kodeverkClient.finnLandkode("DK") }.returns("DNK")

        val listOverSeder = listOf(
            ForenkletSED("eb938171a4cb4e658b3a6c011962d204", SedType.P2100, SedStatus.RECEIVED),
            ForenkletSED("eb938171a4cb4e658b3a6c011962d205", SedType.P5000, SedStatus.SENT),
            ForenkletSED("eb938171a4cb4e658b3a6c011962d504", SedType.P7000, SedStatus.RECEIVED),
            ForenkletSED("eb938171a4cb4e658b3a6c011962d205", SedType.H120, SedStatus.RECEIVED)
        )
        val mockBuc = CustomMockServer.mockBuc("147729", BucType.P_BUC_02, listOverSeder)

        every { personService.hentPerson(NorskIdent("28105424630")) } returns null

        CustomMockServer()
            .medSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d204", "src/test/resources/eux/sed/P2100-PinDK-NAV.json")
            .medSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d205", "src/test/resources/eux/sed/P5000-NAV.json")
            .medSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d504", "src/test/resources/eux/sed/P7000-NAV.json")
            .medMockBuc("/buc/147729", mockBuc)
            .medEndring()

        sendMeldingString(
            mockHendelse(
                bucType = BucType.P_BUC_02,
                sedType = SedType.P7000,
                avsenderLand = "DK",
                docId = "eb938171a4cb4e658b3a6c011962d504"
            )
        )

        assertTrue(isMessageInlog("Endringsmelding: OPPRETT, med nye personopplysninger"))

        CustomMockServer().verifyRequestWithBody(
            "/api/v1/endringer", """
                {
                  "personopplysninger" : [ {
                    "endringstype" : "OPPRETT",
                    "ident" : "11067122781",
                    "opplysningstype" : "UTENLANDSKIDENTIFIKASJONSNUMMER",
                    "endringsmelding" : {
                      "@type" : "UTENLANDSKIDENTIFIKASJONSNUMMER",
                      "kilde" : "DK:D005",
                      "identifikasjonsnummer" : "130177-1234",
                      "utstederland" : "DNK"
                    },
                    "opplysningsId" : null
                  } ]
                }
            """
        )
    }

    @Test
    fun `Gitt en sed hendelse med en dansk uid som ikke finnes i pdl skal det opprettes det en endringsmelding til person-mottak`() {
        every { norg2.hentArbeidsfordelingEnhet(any()) } returns Enhet.ID_OG_FORDELING
        every { personService.hentPerson(NorskIdent(fnr)) } returns PersonMock.createWith(
            fnr = fnr,
            aktoerId = AktoerId("1231231231"),
            uid = emptyList()
        )
        every { kodeverkClient.finnLandkode("DK") }.returns("DNK")

        val listOverSeder = listOf(ForenkletSED("b12e06dda2c7474b9998c7139c841646", SedType.P2100, SedStatus.RECEIVED))
        val mockBuc = CustomMockServer.mockBuc("147729", BucType.P_BUC_02, listOverSeder)

        CustomMockServer()
            .medEndring()
            .medSed("/buc/147729/sed/b12e06dda2c7474b9998c7139c841646", "src/test/resources/eux/sed/P2100-PinDK-NAV.json")
            .medMockBuc("/buc/147729", mockBuc)


        sendMeldingString(javaClass.getResource("/eux/hendelser/P_BUC_01_P2000-avsenderDK.json")!!.readText())

        assertTrue(isMessageInlog("Endringsmelding: OPPRETT, med nye personopplysninger"))
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
                avsenderLand = "SE",
                docId = "eb938171a4cb4e658b3a6c011962d204"
            )
        )
        sedListenerIdent.getLatch().await(20, TimeUnit.SECONDS)

        assertTrue(isMessageInlog("NoUpdate(description=Bruker har ikke utenlandsk ident fra avsenderland (SE), metricTagValueOverride=Bruker har ikke utenlandsk ident fra avsenderland)"))
    }

    val mockedPerson = PersonMock.createWith(
        fnr = fnr,
        aktoerId = AktoerId("1231231231"),
        uid = emptyList()
    )
}

