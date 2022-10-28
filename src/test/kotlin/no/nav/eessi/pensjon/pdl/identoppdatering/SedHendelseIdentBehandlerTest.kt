package no.nav.eessi.pensjon.pdl.identoppdatering

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.UtenlandskId
import no.nav.eessi.pensjon.eux.model.sed.PinItem
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.oppgave.OppgaveHandler
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import no.nav.eessi.pensjon.pdl.adresseoppdatering.SedListenerAdresseIT
import no.nav.eessi.pensjon.pdl.identoppdatering.TestDataPDL.identifisertPerson
import no.nav.eessi.pensjon.pdl.identoppdatering.TestDataPDL.personFraPDL
import no.nav.eessi.pensjon.pdl.identoppdatering.TestDataPDL.sed
import no.nav.eessi.pensjon.pdl.identoppdatering.TestDataPDL.utenlandskIdentifikasjonsnummer
import no.nav.eessi.pensjon.pdl.validering.LandspesifikkValidering
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.retry.annotation.EnableRetry
import org.springframework.stereotype.Component
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.web.client.HttpClientErrorException

private const val FNR = "11067122781"
private const val SVENSK_FNR = "512020-1234"
private const val FINSK_FNR = "130177-308T"

private const val SVERIGE = "SE"
private const val NORGE = "NO"

@ActiveProfiles("retryConfigOverride")
@SpringJUnitConfig(classes = [
    SedHendelseIdentBehandler::class,
    IdentOppdatering::class,
    BehandleIdentRetryLogger::class,
    LandspesifikkValidering::class,
    TestSedHendelseIdentBehandlerRetryConfig::class]
)
@EnableRetry
internal class SedHendelseIdentBehandlerTest {

    @MockkBean
    lateinit var euxService: EuxService

    @MockkBean
    lateinit var personService: PersonService

    @MockkBean
    lateinit var kodeverkClient: KodeverkClient

    @MockkBean
    lateinit var personMottakKlient: PersonMottakKlient

    @MockkBean
    lateinit var oppgaveHandler: OppgaveHandler

    @Autowired
    lateinit var sedHendelseIdentBehandler: SedHendelseIdentBehandler

    @Test
    fun `Gitt at vi har en SED med norsk fnr som skal oppdateres til pdl, der PDL har en aktoerid inne i PDL saa skal Oppdateringsmeldingen til PDL ha norsk FNR og ikke aktoerid`() {
        every { kodeverkClient.finnLandkode("SE") } returns "SWE"
        every { kodeverkClient.finnLandkode("FI") } returns "FIN"
        every { kodeverkClient.finnLandkode("FIN") } returns "FI"

        val identifisertPerson= identifisertPerson(uidFraPdl = listOf(utenlandskIdentifikasjonsnummer(SVENSK_FNR)))

        every { personService.hentPerson(NorskIdent(FNR)) } returns
                personFraPDL(id = FNR).copy(identer = listOf(IdentInformasjon("1234567891234", IdentGruppe.AKTORID), IdentInformasjon(FNR, IdentGruppe.FOLKEREGISTERIDENT)))
                        .copy(utenlandskIdentifikasjonsnummer = listOf(utenlandskIdentifikasjonsnummer(FINSK_FNR).copy(utstederland = "FIN")))

        every { euxService.hentSed(any(), any()) } returns
                sed(id = FNR, land = NORGE, pinItem =  listOf(
                        PinItem(identifikator = "5 12 020-1234", land = SVERIGE),
                        PinItem(identifikator = FNR, land = NORGE))
                )

        every { oppgaveHandler.opprettOppgaveForUid(any(), UtenlandskId( SVENSK_FNR, SVERIGE), identifisertPerson) } returns false
        every { oppgaveHandler.opprettOppgaveForUid(any(), any(), any()) } returns true

        every { personMottakKlient.opprettPersonopplysning(any()) } throws HttpClientErrorException(HttpStatus.LOCKED)

        val ex = org.junit.jupiter.api.assertThrows<HttpClientErrorException> {
            sedHendelseIdentBehandler.behandle(SedListenerAdresseIT.enSedHendelse().toJson())
        }

        assertEquals(HttpStatus.LOCKED, ex.statusCode)

        verify(exactly = 3) { personMottakKlient.opprettPersonopplysning(any()) }

    }
}

@Profile("retryConfigOverride")
@Component("identBehandlerRetryConfig")
data class TestSedHendelseIdentBehandlerRetryConfig(val initialRetryMillis: Long = 10L)