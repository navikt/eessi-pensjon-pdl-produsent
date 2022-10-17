package no.nav.eessi.pensjon.pdl

import no.nav.eessi.pensjon.models.PdlEndringOpplysning
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.util.*

@Component
class PersonMottakKlient(private val personMottakRestTemplate: RestTemplate) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PersonMottakKlient::class.java) }

    @Retryable( // Vi gjør retry når det er lås på objektet
        include = [HttpClientErrorException::class],
        exceptionExpression = "statusCode.value == 423",
        backoff = Backoff(delayExpression = "@personMottakKlientRetryConfig.initialRetryMillis", maxDelay = 200000L, multiplier = 3.0)
    )
    internal fun opprettPersonopplysning(endringer: PdlEndringOpplysning): Boolean {
        val foersteEndring = endringer.personopplysninger.first()
        logger.info("Endringsmelding: ${foersteEndring.endringstype}, med nye personopplysninger av type ${foersteEndring.endringsmelding.type}")

        val httpEntity = HttpEntity(endringer.toJson(), createHeaders())

        val response = personMottakRestTemplate.exchange(
            "/api/v1/endringer",
            HttpMethod.POST,
            httpEntity,
            String::class.java
        )
        // Siden vi bruker DefaultResponseErrorHandler vil vi ikke komme hit dersom kallet over returnerer 4xx eller 5xx
        // - da vil det kastes exception, se DefaultResponseErrorHandler.handleError
        logger.info("Endringresponse StatusCode: ${response.statusCode}, Body: ${response.body}")
        return response.statusCode.is2xxSuccessful
    }

    private fun createHeaders(): HttpHeaders {
        val httpHeaders = HttpHeaders()
        httpHeaders.add("Nav-Call-Id", UUID.randomUUID().toString())
        httpHeaders.add("Tema", "PEN")
        httpHeaders.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString())
        return httpHeaders
    }

}
// Dette er gjort slik for at vi skal kunne overstyre tiden mellom retries i tester
@Component
data class PersonMottakKlientRetryConfig(val initialRetryMillis: Long = 20000L)
