package no.nav.eessi.pensjon.listeners

import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.eux.EuxDokumentHelper
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.personidentifisering.PersonIdenter
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import no.nav.eessi.pensjon.personidentifisering.UtenlandskPin
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endring
import no.nav.eessi.pensjon.personoppslag.pdl.model.Metadata
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskIdentifikasjonsnummer
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.kafka.support.Acknowledgment
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertEquals

internal class SedMottattListenerTest {

    private val acknowledgment = mockk<Acknowledgment>(relaxUnitFun = true)
    private val cr = mockk<ConsumerRecord<String, String>>(relaxed = true)
    private val personidentifiseringService = mockk<PersonidentifiseringService>(relaxed = true)
    private val sedDokumentHelper = mockk<EuxDokumentHelper>(relaxed = true)

    private val kodeverkClient = mockk<KodeverkClient>(relaxed = true)

    private val sedListener = SedMottattListener(
        personidentifiseringService,
        sedDokumentHelper,
        "test")

    @BeforeEach
    fun setup() {
        sedListener.initMetrics()
    }



    @Test
    fun `gitt en gyldig sedHendelse når sedMottatt hendelse konsumeres så ack melding`() {
        sedListener.consumeSedMottatt(String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_01_P2000.json"))), cr, acknowledgment)

        verify(exactly = 1) { acknowledgment.acknowledge() }
    }

    @Test
    fun `gitt en exception ved sedMottatt så ack melding`() {
        sedListener.consumeSedMottatt("Explode!", cr, acknowledgment)

        verify(exactly = 1) { acknowledgment.acknowledge() }
    }

    @Test
    fun `Gitt en svensk Uid som allerede er registrert i pdl naar duplikat sjekk utfores saa returner true`() {
        val identPerson = IdentifisertPerson(
            PersonIdenter(Fodselsnummer.fra("1234567891236540"), listOf(UtenlandskPin("FREG", "1234567891236540", "SE"))),
            listOf(UtenlandskIdentifikasjonsnummer("1234567891236540", "SWE", false, metadata = Metadata(emptyList<Endring>(), false, "FREG", "321654"))))

        val validident = identPerson.personIdenterFraSed.finnesAlleredeIPDL(identPerson.uidFraPdl.map { it.identifikasjonsnummer })

        assertEquals(validident, true)

    }

    @Test
    fun `Gitt en svensk Uid som ikke er registrert i pdl naar duplikat sjekk utfores saa returner false`() {
        val identPerson = IdentifisertPerson(
            PersonIdenter(Fodselsnummer.fra("11067122781"), listOf(UtenlandskPin("FREG", "521552123456", "SE"))),
            listOf(UtenlandskIdentifikasjonsnummer("1234567891236540", "SWE", false, metadata = Metadata(emptyList<Endring>(), false, "FREG", "321654"))))

        val validident = identPerson.personIdenterFraSed.finnesAlleredeIPDL(identPerson.uidFraPdl.map { it.identifikasjonsnummer })

        assertEquals(validident, false)

    }



}
