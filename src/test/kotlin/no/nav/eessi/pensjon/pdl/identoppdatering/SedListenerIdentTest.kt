package no.nav.eessi.pensjon.pdl.identoppdatering

import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.oppgave.OppgaveHandler
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import no.nav.eessi.pensjon.pdl.validering.LandspesifikkValidering
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.kafka.support.Acknowledgment
import java.nio.file.Files
import java.nio.file.Paths

internal class SedListenerIdentTest {

    private val acknowledgment = mockk<Acknowledgment>(relaxUnitFun = true)
    private val cr = mockk<ConsumerRecord<String, String>>(relaxed = true)
    private val euxService = mockk<EuxService>(relaxed = true)
    private val personMottakKlient = mockk<PersonMottakKlient>(relaxed = true)
    private val kodeverkClient = mockk<KodeverkClient>(relaxed = true)
    private val oppgaveHandler = mockk<OppgaveHandler>(relaxed = true)
    private val personService  = mockk<PersonService>(relaxed = true)
    private val landspesifikkValidering = mockk<LandspesifikkValidering>(relaxed = true)

    private lateinit var sedListenerIdent: SedListenerIdent

    @BeforeEach
    fun setup() {
        val vurderIdentoppdatering = VurderIdentoppdatering(
                euxService,
                oppgaveHandler,
                kodeverkClient,
                personService,
                landspesifikkValidering
        )

        sedListenerIdent = SedListenerIdent(
                SedHendelseIdentBehandler(
                        vurderIdentoppdatering,
                        personMottakKlient,
                        oppgaveHandler,
                        "test"
                ),
        )
        sedListenerIdent.initMetrics()
    }

    @Test
    fun `gitt en gyldig sedHendelse når sedMottatt hendelse konsumeres så ack melding`() {
        sedListenerIdent.consumeSedMottatt(Files.readString(Paths.get("src/test/resources/eux/hendelser/P_BUC_01_P2000.json")), cr, acknowledgment)

        verify(exactly = 1) { acknowledgment.acknowledge() }
    }

    @Test
    fun `gitt en exception ved sedMottatt så kast exception`() {

        assertThrows<Exception> {
            sedListenerIdent.consumeSedMottatt("Explode!", cr, acknowledgment)
        }
    }

}