package no.nav.eessi.pensjon.pdl

import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.document.SedDokumentfiler
import no.nav.eessi.pensjon.models.PdlEndringOpplysning
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.exchange
import org.springframework.web.client.postForObject
import java.net.URI

@Component
class PersonMottakKlient(private val personMottakUsernameOidcRestTemplate: RestTemplate) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(PersonMottakKlient::class.java) }

    internal fun opprettPersonopplysning(personopplysning: PdlEndringOpplysning): Boolean {
        logger.info("Henter PDF for SED og tilhørende vedlegg for rinaSakId: ")

        val response = personMottakUsernameOidcRestTemplate.exchange(
                        URI("/api/v1/endringer"),
                        HttpMethod.POST,
                        null,
                        String::class.java
                    )
        return response.statusCode.is2xxSuccessful
    }

}
