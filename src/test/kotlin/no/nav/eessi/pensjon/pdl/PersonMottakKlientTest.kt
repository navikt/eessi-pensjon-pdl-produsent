package no.nav.eessi.pensjon.pdl

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.models.EndringsmeldingUID
import no.nav.eessi.pensjon.models.PdlEndringOpplysning
import no.nav.eessi.pensjon.models.Personopplysninger
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endringstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Opplysningstype
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate

internal class PersonMottakKlientTest {

    private var restTemplate: RestTemplate = mockk()

    private lateinit var personMottakKlient: PersonMottakKlient

    @BeforeEach
    fun setUp() {
        personMottakKlient = PersonMottakKlient(restTemplate)
    }

    @Test
    fun `opprettPersonopplysning - happy day`() {
        restTemplateCall() returns ResponseEntity("Yo", HttpStatus.ACCEPTED)

        personMottakKlient.opprettPersonopplysning(PdlEndringOpplysning(listOf(dummyPersonOpplysninger)))

        verifyRestTemplateInvocations(1)
    }

    @Test
    fun `opprettPersonopplysning - bad request throws`() {
        restTemplateCall() throws HttpClientErrorException(HttpStatus.BAD_REQUEST)

        val ex = assertThrows<HttpClientErrorException> {
            personMottakKlient.opprettPersonopplysning(PdlEndringOpplysning(listOf(dummyPersonOpplysninger)))
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)

        verifyRestTemplateInvocations(1)
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