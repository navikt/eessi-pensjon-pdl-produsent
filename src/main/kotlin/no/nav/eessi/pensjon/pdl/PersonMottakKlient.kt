package no.nav.eessi.pensjon.pdl

import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.models.PdlEndringOpplysning
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.net.URI
import java.util.*

@Component
class PersonMottakKlient(private val personMottakUsernameOidcRestTemplate: RestTemplate) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PersonMottakKlient::class.java) }

    internal fun opprettPersonopplysning(personopplysning: PdlEndringOpplysning): Boolean {
        logger.info("Henter PDF for SED og tilhørende vedlegg for rinaSakId: ")

        val httpEntity = HttpEntity(personopplysning.toJson(), createHeaders())

        val response = personMottakUsernameOidcRestTemplate.exchange(
            URI("/api/v1/endringer"),
            HttpMethod.POST,
            httpEntity,
            String::class.java
        )
        return response.statusCode.is2xxSuccessful
    }

    private fun createHeaders(): HttpHeaders? {
        val httpHeaders = HttpHeaders()
        httpHeaders.add("Nav-Call-Id", UUID.randomUUID().toString())
        httpHeaders.add("Tema", "PEN")
        httpHeaders.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString())
        return httpHeaders
    }

}
