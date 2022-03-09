package no.nav.eessi.pensjon.pdl.integrationtest

import no.nav.eessi.pensjon.EessiPensjonApplication
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.personoppslag.pdl.PersonMock
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId
import no.nav.eessi.pensjon.personoppslag.pdl.model.Person
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskIdentifikasjonsnummer
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertTrue

@SpringBootTest( classes = [IntegrationBase.TestConfig::class, KafkaTestConfig::class, EessiPensjonApplication::class])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(
    topics = [PDL_PRODUSENT_TOPIC_MOTTATT],
    brokerProperties = ["log.dir=/tmp/SvenskUidiPDLerFaktiskIdentiskMedSed"]
)
class SvenskUidiPDLerFaktiskIdentiskMedSedUidIntegrasjonsTest: IntegrationBase()  {
    val fnr = "11067122781"

    override fun getMockNorg2enhet(): Enhet {
        return Enhet.PENSJON_UTLAND
    }

    override fun getMockPerson(): Person? {
        return PersonMock.createWith(
            fnr = fnr,
            landkoder = true,
            aktoerId = AktoerId("65466565"),
            uid = listOf(
                UtenlandskIdentifikasjonsnummer(
                    identifikasjonsnummer = "195402021234",
                    utstederland = "SWE",
                    opphoert = false,
                    metadata = PersonMock.createMetadata()
                )
            )
        )
    }

    @Test
    fun `Gitt PDLuid som sjekke med SEDuid er faktisk identisk oppgave opprettes ikke avslutter hendelse `() {
//        "195402021234, 540202-1234, true",
        val listOverSeder = listOf(mockForenkletSed("eb938171a4cb4e658b3a6c011962d204", SedType.P15000, SedStatus.RECEIVED))
        val mockBuc = mockBuc("147729", BucType.P_BUC_10, listOverSeder)
        val mockPin = listOf(mockPin(fnr, "NO"),
            mockPin("540202-1234", "SE"))
        val mockSed = mockSedUtenPensjon(sedType = SedType.P15000, pin = mockPin)

        CustomMockServer()
            .mockSTSToken()
            .medMockSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d204", mockSed)
            .medMockBuc("/buc/147729", mockBuc)
            .medKodeverk("/api/v1/hierarki/LandkoderSammensattISO2/noder", "src/test/resources/kodeverk/landkoderSammensattIso2.json")

        val hendelseJson = mockHendlese(avsenderLand = "SE", bucType = BucType.P_BUC_10, sedType = SedType.P15000, docId = "eb938171a4cb4e658b3a6c011962d204")

        initAndRunContainer(PDL_PRODUSENT_TOPIC_MOTTATT).also {
            it.sendMsgOnDefaultTopic(hendelseJson)
            it.waitForlatch(sedListener)
        }
        assertTrue(validateSedMottattListenerLoggingMessage("Oppretter ikke oppgave, Det som finnes i PDL er faktisk likt det som finnes i SED, avslutter"))

    }

}




