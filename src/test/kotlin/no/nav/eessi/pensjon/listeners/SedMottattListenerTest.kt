package no.nav.eessi.pensjon.listeners

import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.eux.EuxDokumentHelper
import no.nav.eessi.pensjon.eux.UtenlandskPersonIdentifisering
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import no.nav.eessi.pensjon.pdl.filtrering.PdlFiltrering
import no.nav.eessi.pensjon.pdl.validering.PdlValidering
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.kafka.support.Acknowledgment
import java.nio.file.Files
import java.nio.file.Paths

internal class SedMottattListenerTest {

    private val acknowledgment = mockk<Acknowledgment>(relaxUnitFun = true)
    private val cr = mockk<ConsumerRecord<String, String>>(relaxed = true)
    private val personidentifiseringService = mockk<PersonidentifiseringService>(relaxed = true)
    private val sedDokumentHelper = mockk<EuxDokumentHelper>(relaxed = true)
    private val personMottakKlient = mockk<PersonMottakKlient>(relaxed = true)
    private val utenlandskPersonIdentifisering = mockk<UtenlandskPersonIdentifisering>(relaxed = true)
    private val pdlFiltrering = mockk<PdlFiltrering>(relaxed = true)
    private val pdlValidering = mockk<PdlValidering>(relaxed = true)

    private val sedListener = SedListener(
        personidentifiseringService,
        sedDokumentHelper,
        personMottakKlient,
        utenlandskPersonIdentifisering,
        pdlFiltrering,
        pdlValidering,
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

}
