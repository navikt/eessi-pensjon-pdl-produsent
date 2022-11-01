package no.nav.eessi.pensjon.pdl.identoppdatering

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.models.SedHendelse
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
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

@ActiveProfiles("retryConfigOverride")
@SpringJUnitConfig(classes = [
    SedHendelseIdentBehandler::class,
    SedHendelseIdentBehandlerRetryLogger::class,
    TestSedHendelseIdentBehandlerRetryConfig::class]
)
@EnableRetry
private class SedHendelseIdentBehandlerTest {

    @MockkBean
    lateinit var identOppdatering: IdentOppdatering

    @MockkBean
    lateinit var personMottakKlient: PersonMottakKlient

    @Autowired
    lateinit var sedHendelseIdentBehandler: SedHendelseIdentBehandler

    @Test
    fun `Gitt en at vi får 423 LOCKED fra PDL så gjør vi retry på hele prosessen`() {

        every { identOppdatering.oppdaterUtenlandskIdent(any()) } throws HttpClientErrorException(HttpStatus.LOCKED)

        val ex = org.junit.jupiter.api.assertThrows<HttpClientErrorException> {
            sedHendelseIdentBehandler.behandle(enSedHendelse())
        }

        assertEquals(HttpStatus.LOCKED, ex.statusCode)

        verify(exactly = 3) { identOppdatering.oppdaterUtenlandskIdent(any()) }
        verify(exactly = 0) { personMottakKlient.opprettPersonopplysning(any()) }
    }

    fun enSedHendelse() = SedHendelse(
        sektorKode = "P",
        bucType = BucType.P_BUC_01,
        sedType = SedType.P2100,
        rinaSakId = "74389487",
        rinaDokumentId = "743982",
        rinaDokumentVersjon = "1",
        avsenderNavn = "Svensk institusjon",
        avsenderLand = "SE"
    ).toJson()

}

@Profile("retryConfigOverride")
@Component("sedHendelseIdentBehandlerRetryConfig")
data class TestSedHendelseIdentBehandlerRetryConfig(val initialRetryMillis: Long = 10L)