package no.nav.eessi.pensjon.pdl.adresseoppdatering

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.models.PdlEndringOpplysning
import no.nav.eessi.pensjon.models.SedHendelseModel
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import no.nav.eessi.pensjon.pdl.integrationtest.CustomMockServer
import no.nav.eessi.pensjon.pdl.integrationtest.IntegrationBase
import no.nav.eessi.pensjon.pdl.integrationtest.KafkaTestConfig
import no.nav.eessi.pensjon.pdl.oppdatering.SedListenerAdresse
import no.nav.eessi.pensjon.personoppslag.pdl.PersonMock
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId
import no.nav.eessi.pensjon.personoppslag.pdl.model.Bostedsadresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskAdresse
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit


@SpringBootTest( classes = [KafkaTestConfig::class, IntegrationBase.TestConfig::class])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(
    controlledShutdown = true,
    topics = ["eessi-basis-sedMottatt-v1"]
)
internal class SedListenerAdresseIT : IntegrationBase(){
    val fnr = "11067122781"

    @Autowired
    lateinit var adresseListener: SedListenerAdresse

    @Autowired
    var personMottakKlient: PersonMottakKlient = mockk()

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
    fun `Gitt en sed der adresse allerede finnes i PDL, og adresse er lik så skal PDL oppdateres med ny dato`() {

        val uAdresse = Bostedsadresse(
            LocalDateTime.now(),
            LocalDateTime.now(),
            null,
            UtenlandskAdresse(
                adressenavnNummer = "654",
                bySted = "Oslo",
                bygningEtasjeLeilighet = null,
                landkode = "NO",
                postboksNummerNavn = "645",
                postkode = "987",
                regionDistriktOmraade = null
            ),
            metadata = mockk()
        )
        //given
        val listOverSeder = listOf(mockForenkletSed("eb938171a4cb4e658b3a6c011962d204", SedType.P2100, SedStatus.RECEIVED))
        val mockBuc = mockBuc("147729", BucType.P_BUC_10, listOverSeder)
        CustomMockServer()
            .medSed("/buc/147729/sed/eb938171a4cb4e658b3a6c011962d204", "src/test/resources/eux/sed/P2100-PinDK-NAV.json")
            .medMockBuc("/buc/147729", mockBuc)

        every { personService.hentPerson(NorskIdent( "11067122781")) } returns mockedPerson
        // dersom sed sin boadresse er den samme som boadressen i PDL så skal vi sende adressen med ny dato som en adresseendringsmelding til PDL

        //when
        kafkaTemplate.sendDefault(javaClass.getResource("/eux/hendelser/P_BUC_01_P2000.json").readText())


        //then
        adresseListener.latch.await(20, TimeUnit.SECONDS)
        assertEquals(0, adresseListener.latch.count)



        verify (atLeast = 1) { personMottakKlient.opprettPersonopplysning(PdlEndringOpplysning(any())) }

        //TODO: sjekke at adresse er lik og at det skjer en oppdatering med ny timestamp (dobbeltsjekk dette)
    }







}