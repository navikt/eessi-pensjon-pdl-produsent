package no.nav.eessi.pensjon.pdl.integrationtest

import io.mockk.every
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.personoppslag.pdl.PersonMock
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskIdentifikasjonsnummer
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockserver.client.MockServerClient
import org.mockserver.model.HttpRequest
import org.mockserver.model.JsonBody
import org.mockserver.verify.VerificationTimes
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles

@SpringBootTest( classes = [KafkaTestConfig::class, IntegrationBase.TestConfig::class])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(
    controlledShutdown = true,
    topics = [PDL_PRODUSENT_TOPIC_MOTTATT]
)
class OpprettMeldingEllerOppgaveIntegrationTest : IntegrationBase() {

    val fnr = "11067122781"

    @Test
    fun `Gitt en hendelse med flere sed i buc og en dansk uid som ikke finnes i pdl skal det opprettes det en endringsmelding til person-mottak`() {
        every { norg2.hentArbeidsfordelingEnhet(any()) } returns Enhet.NFP_UTLAND_OSLO
        every { personService.hentPerson(NorskIdent(fnr))} returns PersonMock.createWith(
            fnr = fnr,
            aktoerId = AktoerId("1231231231"),
            uid = emptyList()
        )

        val listOverSeder = listOf(
            mockForenkletSed("eb938171a4cb4e658b3a6c011962d204", SedType.P2100, SedStatus.RECEIVED),
            mockForenkletSed("eb938171a4cb4e658b3a6c011962d205", SedType.P5000, SedStatus.SENT),
            mockForenkletSed("eb938171a4cb4e658b3a6c011962d504", SedType.P7000, SedStatus.RECEIVED),
            mockForenkletSed("eb938171a4cb4e658b3a6c011962d205", SedType.H120, SedStatus.RECEIVED)
        )
        val mockBuc = mockBuc("147729", BucType.P_BUC_02, listOverSeder)

        every { personService.hentPerson(NorskIdent("28105424630")) } returns null

        CustomMockServer()
            .mockSTSToken()
            .medSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d204", "src/test/resources/eux/sed/P2100-PinDK-NAV.json")
            .medSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d205", "src/test/resources/eux/sed/P5000-NAV.json")
            .medSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d504", "src/test/resources/eux/sed/P7000-NAV.json")
            .medMockBuc("/buc/147729", mockBuc)
            .medKodeverk("/api/v1/hierarki/LandkoderSammensattISO2/noder", "src/test/resources/kodeverk/landkoderSammensattIso2.json")
            .medEndring()

        sendMeldingString(
            mockHendelse(
                bucType = BucType.P_BUC_02,
                sedType = SedType.P7000,
                docId = "eb938171a4cb4e658b3a6c011962d504"
            )
        )

        assertTrue(validateSedMottattListenerLoggingMessage("SED av type: P2100, status: RECEIVED"))
        assertTrue(validateSedMottattListenerLoggingMessage("SED av type: P5000, status: SENT"))
        assertTrue(validateSedMottattListenerLoggingMessage("SED av type: P7000, status: RECEIVED"))
        assertTrue(validateSedMottattListenerLoggingMessage("Oppretter endringsmelding med nye personopplysninger fra avsenderLand:"))

        val check = """
              "personopplysninger" : [ {
                "endringstype" : "OPPRETT",
                "ident" : "11067122781",
                "opplysningstype" : "UTENLANDSKIDENTIFIKASJONSNUMMER",
                "endringsmelding" : {
                  "@type" : "UTENLANDSKIDENTIFIKASJONSNUMMER",
                  "identifikasjonsnummer" : "130177-1234",
                  "utstederland" : "DNK",
                  "kilde" : "DK:D005"
                },
                "opplysningsId" : null
              } ]
            }
        """.trimIndent()
        CustomMockServer().verifyRequestWithBody("/api/v1/endringer", check,1)
    }

    @Test
    fun `Gitt en sed hendelse med en dansk uid som ikke finnes i pdl skal det opprettes det en endringsmelding til person-mottak`() {
        every { norg2.hentArbeidsfordelingEnhet(any()) } returns Enhet.ID_OG_FORDELING
        every { personService.hentPerson(NorskIdent(fnr)) } returns PersonMock.createWith(
            fnr = fnr,
            aktoerId = AktoerId("1231231231"),
            uid = emptyList()
        )

        val listOverSeder = listOf(mockForenkletSed("eb938171a4cb4e658b3a6c011962d204", SedType.P2100, SedStatus.RECEIVED))
        val mockBuc = mockBuc("147729", BucType.P_BUC_02, listOverSeder)

        CustomMockServer()
            .mockSTSToken()
            .medEndring()
            .medSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d204", "src/test/resources/eux/sed/P2100-PinDK-NAV.json")
            .medMockBuc("/buc/147729", mockBuc)
            .medKodeverk("/api/v1/hierarki/LandkoderSammensattISO2/noder", "src/test/resources/kodeverk/landkoderSammensattIso2.json")

        sendMelding("/eux/hendelser/P_BUC_01_P2000-avsenderDK.json")

        assertTrue(validateSedMottattListenerLoggingMessage("Oppretter endringsmelding med nye personopplysninger fra avsenderLand:"))
    }

    @Test
    fun `Gitt en sed hendelse dansk uid som er forskjellig fra det som finnes i PDL skal det ack med logg Det finnes allerede en annen uid fra samme land Og Oppgave skal opprettes `() {
        every { norg2.hentArbeidsfordelingEnhet(any()) } returns  Enhet.PENSJON_UTLAND
        every { personService.hentPerson(NorskIdent(fnr)) } returns  PersonMock.createWith(
            fnr = fnr,
            landkoder = true,
            aktoerId = AktoerId("65466565"),
            uid = listOf(
                UtenlandskIdentifikasjonsnummer(
                    identifikasjonsnummer = "130177-1234",
                    utstederland = "DNK",
                    opphoert = false,
                    metadata = PersonMock.createMetadata()
                )
            )
        )

        val listOverSeder = listOf(mockForenkletSed("eb938171a4cb4e658b3a6c011962d204", SedType.P15000, SedStatus.RECEIVED))
        val mockBuc = mockBuc("147729", BucType.P_BUC_10, listOverSeder)
        val mockPin = listOf(mockPin(fnr, "NO"),
            mockPin("130177-5432", "DK"))
        val mockSed = mockSedUtenPensjon(sedType = SedType.P15000, pin = mockPin)

        CustomMockServer()
            .mockSTSToken()
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
        assertTrue(validateSedMottattListenerLoggingMessage("Det finnes allerede en annen uid fra samme land, opprette oppgave"))
        val check = """
            Opprette oppgave melding p?? kafka: eessi-pensjon-oppgave-v1  melding: {
              "sedType" : null,
              "journalpostId" : null,
              "tildeltEnhetsnr" : "0001",
              "aktoerId" : "65466565",
              "rinaSakId" : "147729",
              "hendelseType" : "MOTTATT",
              "filnavn" : null,
              "oppgaveType" : "PDL"
            }
        """.trimIndent()
        assertTrue(validateSedMottattListenerLoggingMessage(check))
    }
}

