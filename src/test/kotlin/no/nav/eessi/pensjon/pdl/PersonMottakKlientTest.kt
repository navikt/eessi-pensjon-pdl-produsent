package no.nav.eessi.pensjon.pdl

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import no.nav.eessi.pensjon.models.EndringsmeldingUID
import no.nav.eessi.pensjon.models.PdlEndringOpplysning
import no.nav.eessi.pensjon.models.Personopplysninger
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endringstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Opplysningstype
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.retry.annotation.EnableRetry
import org.springframework.stereotype.Component
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate

@ActiveProfiles("retryConfigOverride")
@SpringJUnitConfig(classes = [PersonMottakKlient::class, TestPersonMottakKlientRetryConfig::class])
@EnableRetry
internal class PersonMottakKlientTest {

    @MockkBean
    lateinit var restTemplate: RestTemplate

    @Autowired
    lateinit var personMottakKlient: PersonMottakKlient

    @Test
    fun `opprettPersonopplysning - happy day`() {
        restTemplateCall() returns ResponseEntity("Yo", HttpStatus.ACCEPTED)

        personMottakKlient.opprettPersonopplysning(PdlEndringOpplysning(listOf(dummyPersonOpplysninger)))

        verifyRestTemplateInvocations(1)
    }

    @Test
    fun `opprettPersonopplysning - bad request - no retry`() {
        restTemplateCall() throws HttpClientErrorException(HttpStatus.BAD_REQUEST)

        val ex = assertThrows<HttpClientErrorException> {
            personMottakKlient.opprettPersonopplysning(PdlEndringOpplysning(listOf(dummyPersonOpplysninger)))
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)

        verifyRestTemplateInvocations(1)
    }

    // https://github.com/spring-projects/spring-retry
    @Test
    fun `opprettPersonopplysning - locked - retry 3 times`() {
        restTemplateCall() throws HttpClientErrorException(HttpStatus.LOCKED)

        val ex = assertThrows<HttpClientErrorException> {
            personMottakKlient.opprettPersonopplysning(PdlEndringOpplysning(listOf(dummyPersonOpplysninger)))
        }
        assertEquals(HttpStatus.LOCKED, ex.statusCode)

        verifyRestTemplateInvocations(3)
    }

    private fun restTemplateCall() = every {
        restTemplate.exchange(
            eq("/api/v1/endringer"),
            eq(HttpMethod.POST),
            any<HttpEntity<*>>(),
            eq(String::class.java)
        )
    }

    private fun verifyRestTemplateInvocations(n: Int) {
        verify(exactly = n) {
            restTemplate.exchange(
                eq("/api/v1/endringer"),
                eq(HttpMethod.POST),
                any<HttpEntity<*>>(),
                eq(String::class.java)
            )
        }
    }

    private val dummyPersonOpplysninger = Personopplysninger(
        endringstype = Endringstype.OPPRETT,
        endringsmelding = EndringsmeldingUID(identifikasjonsnummer = "", utstederland = ""),
        ident = "",
        opplysningstype = Opplysningstype.UTENLANDSKIDENTIFIKASJONSNUMMER
    )

}

// Brukes for at testen skal gå kjapt
@Profile("retryConfigOverride")
@Component("personMottakKlientRetryConfig")
data class TestPersonMottakKlientRetryConfig(val initialRetryMillis: Long = 10L)