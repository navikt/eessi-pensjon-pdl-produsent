package no.nav.eessi.pensjon.pdl.identoppdatering

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_02
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_10
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.klienter.pesys.BestemSakKlient
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.oppgaverouting.Enhet
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
import org.springframework.web.client.RestTemplate
import java.util.concurrent.TimeUnit

@SpringBootTest( classes = [KafkaTestConfig::class, IntegrationBase.TestConfig::class])
@ActiveProfiles("integrationtest", "excludeKodeverk")
@DirtiesContext
@EmbeddedKafka(
    controlledShutdown = true,
    topics = [PDL_PRODUSENT_TOPIC_MOTTATT]
)
class SedListenerIdentIT : IntegrationBase() {

    private val acknowledgment = mockk<Acknowledgment>(relaxUnitFun = true)
    private val cr = mockk<ConsumerRecord<String, String>>(relaxed = true)

    @MockkBean(name = "pdlRestTemplate")
    lateinit var pdlRestTemplate: RestTemplate

    @Autowired
    lateinit var sedListenerIdent: SedListenerIdent

    @MockkBean
    lateinit var kodeverkClient: KodeverkClient

    @MockkBean
    lateinit var bestemSakKlient: BestemSakKlient

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
    fun `En sed hendelse uten UID vil resultere i ingen oppdatering`() {
        every { norg2.hentArbeidsfordelingEnhet(any()) } returns Enhet.ID_OG_FORDELING
        every { personService.hentPerson(NorskIdent( fnr)) } returns mockedPerson
        every { kodeverkClient.finnLandkode("NO") } returns "NOR"

        val listOverSeder = listOf(ForenkletSED("eb938171a4cb4e658b3a6c011962d204", SedType.P15000, SedStatus.RECEIVED))
        val mockBuc = CustomMockServer.mockBuc("147729", P_BUC_10, listOverSeder)

        val mockNorskPin = mockPin(fnr, "NO")
        val mockSed = mockSedUtenPensjon(sedType = SedType.P15000, pin = listOf(mockNorskPin))

        CustomMockServer()
            .medMockSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d204", mockSed)
            .medMockBuc("/buc/147729", mockBuc)
            .medEndring()

        sendMeldingString(
            mockHendelse(
                bucType = P_BUC_10,
                sedType = SedType.P15000,
                avsenderLand = "SE",
                docId = "eb938171a4cb4e658b3a6c011962d204"
            )
        )
        sedListenerIdent.getLatch().await(20, TimeUnit.SECONDS)

        assertTrue(isMessageInlog("IngenOppdatering(description=Bruker har ikke utenlandsk ident fra avsenderland (SE), metricTagValueOverride=Bruker har ikke utenlandsk ident fra avsenderland)"))
    }

    @Test
    fun `Gitt en sed-hendelse fra Sverige som sender inn en tysk uid saa skal det stoppes av valideringen`() {
        every { norg2.hentArbeidsfordelingEnhet(any()) } returns  Enhet.ID_OG_FORDELING
        every { personService.hentPerson(NorskIdent( "29087021082")) } returns mockedPerson
        every { kodeverkClient.finnLandkode("DE") } returns "DEU"

        val listOverSeder = listOf(ForenkletSED("b12e06dda2c7474b9998c7139c841646", SedType.P8000, SedStatus.RECEIVED))
        val mockBuc = CustomMockServer.mockBuc("147729", P_BUC_02, listOverSeder)

        CustomMockServer()
            .medSed("/buc/147729/sed/b12e06dda2c7474b9998c7139c841646", "src/test/resources/eux/sed/P8000-TyskPIN.json")
            .medMockBuc("/buc/147729", mockBuc)

        sendMeldingString(javaClass.getResource("/eux/hendelser/P_BUC_01_P2000-avsenderSE.json")!!.readText())
        sedListenerIdent.getLatch().await(20, TimeUnit.SECONDS)

        assertTrue(isMessageInlog("IngenOppdatering(description=Bruker har ikke utenlandsk ident fra avsenderland (SE), metricTagValueOverride=Bruker har ikke utenlandsk ident fra avsenderland)"))

        //sjekker at vi ikke har en ident oppdatering
        CustomMockServer().verifyRequest("/api/v1/endringer", 0)
    }

    @Test
    fun `Gitt en sed hendelse uten avsenderNavn saa sender vi ingen oppdatering`() {
        every { norg2.hentArbeidsfordelingEnhet(any()) } returns Enhet.ID_OG_FORDELING
        every { personService.hentPerson(NorskIdent( "29087021082")) } returns mockedPerson
        every { kodeverkClient.finnLandkode("DK") } returns "DNK"

        val listOverSeder = listOf(ForenkletSED("eb938171a4cb4e658b3a6c011962d204", SedType.P15000, SedStatus.RECEIVED))
        val mockBuc = CustomMockServer.mockBuc("147729", P_BUC_10, listOverSeder)

        CustomMockServer()
            .medSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d204", "src/test/resources/eux/sed/P2100-PinDK-NAV.json")
            .medMockBuc("/buc/147729", mockBuc)


        sendMeldingString(
            mockHendelse(
                bucType = P_BUC_10,
                sedType = SedType.P15000,
                avsenderLand = "DK",
                avsenderNavn = "",
                docId = "eb938171a4cb4e658b3a6c011962d204"
            )
        )
        sedListenerIdent.getLatch().await(20, TimeUnit.SECONDS)

        assertTrue(isMessageInlog("AvsenderNavn er ikke satt, kan derfor ikke lage endringsmelding"))
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
        val mockBuc = CustomMockServer.mockBuc("147729", P_BUC_02, listOverSeder)

        every { personService.hentPerson(NorskIdent("28105424630")) } returns null

        CustomMockServer()
            .medSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d204", "src/test/resources/eux/sed/P2100-PinDK-NAV.json")
            .medSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d205", "src/test/resources/eux/sed/P5000-NAV.json")
            .medSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d504", "src/test/resources/eux/sed/P7000-NAV.json")
            .medMockBuc("/buc/147729", mockBuc)
            .medEndring()

        sendMeldingString(
            mockHendelse(
                bucType = P_BUC_02,
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
        val mockBuc = CustomMockServer.mockBuc("147729", P_BUC_02, listOverSeder)

        CustomMockServer()
            .medEndring()
            .medSed("/buc/147729/sed/b12e06dda2c7474b9998c7139c841646", "src/test/resources/eux/sed/P2100-PinDK-NAV.json")
            .medMockBuc("/buc/147729", mockBuc)


        sendMeldingString(javaClass.getResource("/eux/hendelser/P_BUC_01_P2000-avsenderDK.json")!!.readText())

        assertTrue(isMessageInlog("Endringsmelding: OPPRETT, med nye personopplysninger"))
    }

}

