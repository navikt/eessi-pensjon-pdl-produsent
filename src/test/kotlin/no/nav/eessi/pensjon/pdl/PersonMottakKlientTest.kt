package no.nav.eessi.pensjon.pdl

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.models.EndringsmeldingUID
import no.nav.eessi.pensjon.models.PdlEndringOpplysning
import no.nav.eessi.pensjon.models.Personopplysninger
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endringstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Opplysningstype
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.nio.charset.Charset

internal class PersonMottakKlientTest {

    private var restTemplate: RestTemplate = mockk()
    private lateinit var personMottakKlient: PersonMottakKlient

    private val deugLogger: Logger = LoggerFactory.getLogger("no.nav.eessi.pensjon") as Logger
    private val listAppender = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun setUp() {
        personMottakKlient = PersonMottakKlient(restTemplate)

        listAppender.start()
        deugLogger.addAppender(listAppender)
    }

    @AfterEach
    fun after() {
        println(" ****************************** after ******************************** ")
        listAppender.stop()
    }
    @Test
    fun `opprettPersonopplysning - happy day`() {
        restTemplateCall() returns ResponseEntity("Yo", HttpStatus.ACCEPTED)

        personMottakKlient.opprettPersonopplysning(PdlEndringOpplysning(listOf(dummyPersonOpplysninger())))

        verifyRestTemplateInvocations(1)
    }

    @Test
    fun `opprettPersonopplysning - bad request throws`() {
        restTemplateCall() throws HttpClientErrorException(HttpStatus.BAD_REQUEST)

        val ex = assertThrows<HttpClientErrorException> {
            personMottakKlient.opprettPersonopplysning(PdlEndringOpplysning(listOf(dummyPersonOpplysninger())))
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)

        verifyRestTemplateInvocations(1)
    }

    @Test
    fun `Gitt at vi mottar en opprettPersonopplysning - conflict 409 request throws`() {
        restTemplateCall() throws HttpClientErrorException(HttpStatus.CONFLICT, "Kontaktadressen er allerede registrert som bostedsadresse, Ingen Oppdatering")

        val response = personMottakKlient.opprettPersonopplysning(PdlEndringOpplysning(listOf(dummyPersonOpplysninger(Opplysningstype.KONTAKTADRESSE))))

        assertTrue(response)
        assertTrue(isMessageInlog("Kontaktadressen er allerede registrert som bostedsadresse, Ingen Oppdatering"))

        verifyRestTemplateInvocations(1)
    }

    @ParameterizedTest
    @EnumSource(Opplysningstype::class, mode = EnumSource.Mode.EXCLUDE, names = ["KONTAKTADRESSE"])
    fun `Gitt en opprettPersonopplysning med kaster en conflict 409 return false`(opplysningstype:Opplysningstype) {
        restTemplateCall() throws HttpClientErrorException(HttpStatus.CONFLICT)

        val ex = assertThrows<HttpClientErrorException> {
            personMottakKlient.opprettPersonopplysning(PdlEndringOpplysning(listOf(dummyPersonOpplysninger(opplysningstype))))
        }
        assertEquals(HttpStatus.CONFLICT, ex.statusCode)

        verifyRestTemplateInvocations(1)
    }

    fun isMessageInlog(keyword: String): Boolean {
        val logsList: List<ILoggingEvent> = listAppender.list
        return logsList.find { logMelding ->
            logMelding.message.contains(keyword)
        }?.message?.isNotEmpty() ?: false
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

    private fun dummyPersonOpplysninger(opplysningstype: Opplysningstype = Opplysningstype.UTENLANDSKIDENTIFIKASJONSNUMMER) = Personopplysninger(
        endringstype = Endringstype.OPPRETT,
        endringsmelding = EndringsmeldingUID(identifikasjonsnummer = "", utstederland = ""),
        ident = "",
        opplysningstype = opplysningstype
    )

}
