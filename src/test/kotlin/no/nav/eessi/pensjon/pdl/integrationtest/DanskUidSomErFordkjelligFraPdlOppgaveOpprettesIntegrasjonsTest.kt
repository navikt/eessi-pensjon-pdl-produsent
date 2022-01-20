package no.nav.eessi.pensjon.pdl.integrationtest

import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.personoppslag.FodselsnummerGenerator
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

@SpringBootTest( classes = [KafkaTestConfig::class], properties = ["spring.main.allow-bean-definition-overriding=true"])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(
    topics = [PDL_PRODUSENT_TOPIC_MOTTATT],
    brokerProperties = ["log.dir=/tmp/embedded-kafka-NyDanskUtenUidIntegrasjonsTest"]
)



class DanskUidSomErFordkjelligFraPdlOppgaveOpprettesIntegrasjonsTest : IntegrationBase() {

    val fnr = FodselsnummerGenerator.generateFnrForTest(62)

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
                    identifikasjonsnummer = "130177-1234",
                    utstederland = "DNK",
                    opphoert = false,
                    metadata = PersonMock.createMetadata()
                ))
        )
    }

    @Test
    fun `Gitt en sed hendelse dansk uid som er forskjellig fra det som finnes i PDL skal det ack med logg Det finnes allerede en annen uid fra samme land Og Oppgave skal opprettes `() {

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

        val hendelseJson = mockHendlese(bucType = BucType.P_BUC_10, sedType = SedType.P15000, docId = "eb938171a4cb4e658b3a6c011962d204")

        initAndRunContainer(PDL_PRODUSENT_TOPIC_MOTTATT).also {
            it.sendMsgOnDefaultTopic(hendelseJson)
            it.waitForlatch(sedListener)
        }

        assertTrue(validateSedMottattListenerLoggingMessage("Det finnes allerede en annen uid fra samme land, opprette oppgave"))
        val check = """
            Opprette oppgave melding på kafka: eessi-pensjon-oppgave-v1  melding: {
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
//        assertTrue(validateSedMottattListenerLoggingMessage("Opprett oppgave og lagret til s3"))

    }

}

