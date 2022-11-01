package no.nav.eessi.pensjon.pdl.adresseoppdatering

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import no.nav.eessi.pensjon.personoppslag.FodselsnummerGenerator
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.retry.annotation.EnableRetry
import org.springframework.stereotype.Component
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.web.client.HttpClientErrorException

@ActiveProfiles("retryConfigOverride")
@SpringJUnitConfig(classes = [
    SedHendelseBehandler::class,
    Adresseoppdatering::class,
    SedTilPDLAdresse::class,
    SedHendelseBehandlerRetryLogger::class,
    TestSedHendelseBehandlerRetryConfig::class]
)
@EnableRetry
class SedHendelseBehandlerTest {

    @MockkBean
    lateinit var euxService: EuxService

    @MockkBean
    lateinit var personService: PersonService

    @MockkBean
    lateinit var kodeverkClient: KodeverkClient

    @MockkBean
    lateinit var personMottakKlient: PersonMottakKlient

    @Autowired
    lateinit var sedHendelseBehandler: SedHendelseBehandler

    @Test
    fun `Gitt en at vi får 423 LOCKED fra PDL så gjør vi retry på hele prosessen`() {
        val fnr = FodselsnummerGenerator.generateFnrForTest(70)
        every { euxService.hentSed(eq("74389487"), eq("743982")) } returns SedListenerAdresseIT.enSedFraEux(fnr)
        every { personService.hentPerson(NorskIdent(fnr)) } returns SedListenerAdresseIT.enPersonFraPDL(fnr)
        every { kodeverkClient.finnLandkode("SE") } returns "SWE"
        every { personMottakKlient.opprettPersonopplysning(any()) } throws HttpClientErrorException(HttpStatus.LOCKED)

        val ex = assertThrows<HttpClientErrorException> {
            sedHendelseBehandler.behandle(SedListenerAdresseIT.enSedHendelse().toJson())
        }

        assertEquals(HttpStatus.LOCKED, ex.statusCode)

        verify(exactly = 3) { personMottakKlient.opprettPersonopplysning(any()) }
    }

    @Test
    fun `Gitt en at vi får 400 BAD REQUEST fra PDL så gjør vi ikke retry på prosessen`() {
        val fnr = FodselsnummerGenerator.generateFnrForTest(70)
        every { euxService.hentSed(eq("74389487"), eq("743982")) } returns SedListenerAdresseIT.enSedFraEux(fnr)
        every { personService.hentPerson(NorskIdent(fnr)) } returns SedListenerAdresseIT.enPersonFraPDL(fnr)
        every { kodeverkClient.finnLandkode("SE") } returns "SWE"
        every { personMottakKlient.opprettPersonopplysning(any()) } throws HttpClientErrorException(HttpStatus.BAD_REQUEST)

        val ex = assertThrows<HttpClientErrorException> {
            sedHendelseBehandler.behandle(SedListenerAdresseIT.enSedHendelse().toJson())
        }

        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)

        verify(exactly = 1) { personMottakKlient.opprettPersonopplysning(any()) }
    }
}

// Brukes for at testen skal gå kjapt
@Profile("retryConfigOverride")
@Component("sedHendelseBehandlerRetryConfig")
data class TestSedHendelseBehandlerRetryConfig(val initialRetryMillis: Long = 10L)
