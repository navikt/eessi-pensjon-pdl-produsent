package no.nav.eessi.pensjon.pdl.adresseoppdatering

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
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
    SedHendelseBehandlerRetryLogger::class,
    TestSedHendelseBehandlerRetryConfig::class]
)
@EnableRetry
class SedHendelseBehandlerTest {

    @MockkBean
    lateinit var adresseoppdatering: VurderAdresseoppdatering

    @MockkBean
    lateinit var personMottakKlient: PersonMottakKlient

    @Autowired
    lateinit var sedHendelseBehandler: SedHendelseBehandler

    @Test
    fun `Gitt en at vi får 423 LOCKED fra PDL så gjør vi retry på hele prosessen`() {

        every { adresseoppdatering.vurderUtenlandskKontaktadresse(any()) } throws HttpClientErrorException(HttpStatus.LOCKED)

        val ex = assertThrows<HttpClientErrorException> {
            sedHendelseBehandler.behandle(enSedHendelse())
        }

        assertEquals(HttpStatus.LOCKED, ex.statusCode)

        verify(exactly = 3) { adresseoppdatering.vurderUtenlandskKontaktadresse(any()) }
        verify(exactly = 0) { personMottakKlient.opprettPersonopplysning(any()) }
    }


    @Test
    fun `Gitt en at vi får 400 BAD REQUEST fra PDL så gjør vi ikke retry på prosessen`() {

        every { adresseoppdatering.vurderUtenlandskKontaktadresse(any()) } throws HttpClientErrorException(HttpStatus.BAD_REQUEST)

        val ex = assertThrows<HttpClientErrorException> {
            sedHendelseBehandler.behandle(enSedHendelse())
        }

        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)

        verify(exactly = 1) { adresseoppdatering.vurderUtenlandskKontaktadresse(any()) }
        verify(exactly = 0) { personMottakKlient.opprettPersonopplysning(any()) }
    }

    fun enSedHendelse() = SedHendelse(
            sektorKode = "P",
            bucType = P_BUC_01,
            sedType = SedType.P2100,
            rinaSakId = "74389487",
            rinaDokumentId = "743982",
            rinaDokumentVersjon = "1",
            avsenderNavn = "Svensk institusjon",
            avsenderLand = "SE"
        ).toJson()
}

// Brukes for at testen skal gå kjapt
@Profile("retryConfigOverride")
@Component("sedHendelseBehandlerRetryConfig")
data class TestSedHendelseBehandlerRetryConfig(val initialRetryMillis: Long = 10L)
