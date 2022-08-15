package no.nav.eessi.pensjon.pdl.identoppdatering

import io.mockk.every
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.pdl.integrationtest.CustomMockServer
import no.nav.eessi.pensjon.pdl.integrationtest.IntegrationBase
import no.nav.eessi.pensjon.pdl.integrationtest.KafkaTestConfig
import no.nav.eessi.pensjon.pdl.integrationtest.PDL_PRODUSENT_TOPIC_MOTTATT
import no.nav.eessi.pensjon.personoppslag.pdl.model.PersonMock
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskIdentifikasjonsnummer
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import java.util.concurrent.TimeUnit

@SpringBootTest( classes = [KafkaTestConfig::class, IntegrationBase.TestConfig::class])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(
    controlledShutdown = true,
    topics = [PDL_PRODUSENT_TOPIC_MOTTATT]
)
class IdentFinnesIntegrationIT : IntegrationBase() {

    @Autowired(required = true)
    lateinit var sedListenerIdent: SedListenerIdent

    val fnr = "11067122781"

    @Test
    fun `Gitt en sed hendelse med dansk uid i sed som også finnes i pdl så skal vi acke og avslutte på en pen måte`() {
        every { norg2.hentArbeidsfordelingEnhet(any()) } returns  Enhet.ID_OG_FORDELING
        every { personService.hentPerson(NorskIdent(fnr)) } returns PersonMock.createWith(
            fnr = fnr,
            aktoerId = AktoerId("1231231231"),
            uid = listOf(UtenlandskIdentifikasjonsnummer(
                identifikasjonsnummer = "130177-1234",
                utstederland = "DNK",
                opphoert = false,
                metadata = PersonMock.createMetadata()
            ))
        )

        val listOverSeder = listOf(mockForenkletSed("eb938171a4cb4e658b3a6c011962d204", SedType.P2100, SedStatus.RECEIVED))
        val mockBuc = mockBuc("147729", BucType.P_BUC_02, listOverSeder)

        CustomMockServer()
            .medSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d204", "src/test/resources/eux/sed/P2100-PinDK-NAV.json")
            .medMockBuc("/buc/147729", mockBuc)
            .medKodeverk("/api/v1/hierarki/LandkoderSammensattISO2/noder", "src/test/resources/kodeverk/landkoderSammensattIso2.json")

        sendMeldingString(javaClass.getResource("/eux/hendelser/P_BUC_01_P2000-avsenderDK.json").readText())
        sedListenerIdent.getLatch().await(20, TimeUnit.SECONDS)
        assertTrue(validateSedMottattListenerLoggingMessage("PDLuid er identisk med SEDuid. Acket sedMottatt"))
    }

    @Test
    fun `Gitt PDLuid som sjekke med SEDuid er faktisk identisk oppgave opprettes ikke avslutter hendelse `() {
        every { norg2.hentArbeidsfordelingEnhet(any()) } returns Enhet.PENSJON_UTLAND
        every { personService.hentPerson(NorskIdent(fnr)) } returns PersonMock.createWith(
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

        val listOverSeder = listOf(mockForenkletSed("eb938171a4cb4e658b3a6c011962d204", SedType.P15000, SedStatus.RECEIVED))
        val mockBuc = mockBuc("147729", BucType.P_BUC_10, listOverSeder)
        val mockPin = listOf(mockPin(fnr, "NO"),
            mockPin("540202-1234", "SE"))
        val mockSed = mockSedUtenPensjon(sedType = SedType.P15000, pin = mockPin)

        CustomMockServer()
            .medMockSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d204", mockSed)
            .medMockBuc("/buc/147729", mockBuc)
            .medKodeverk("/api/v1/hierarki/LandkoderSammensattISO2/noder", "src/test/resources/kodeverk/landkoderSammensattIso2.json")

        val hendelseJson = mockHendelse(avsenderLand = "SE", bucType = BucType.P_BUC_10, sedType = SedType.P15000, docId = "eb938171a4cb4e658b3a6c011962d204")
        sendMeldingString(hendelseJson)
        sedListenerIdent.getLatch().await(20, TimeUnit.SECONDS)

        assertTrue(validateSedMottattListenerLoggingMessage("Oppretter ikke oppgave, Det som finnes i PDL er faktisk likt det som finnes i SED, avslutter"))
    }

}
