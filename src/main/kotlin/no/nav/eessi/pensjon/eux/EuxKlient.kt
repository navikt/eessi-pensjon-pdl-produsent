package no.nav.eessi.pensjon.eux

import no.nav.eessi.pensjon.eux.model.buc.Buc
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class EuxKlient(private val euxOAuthRestTemplate: RestTemplate) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(EuxKlient::class.java) }

    internal fun hentSedJson(rinaSakId: String, dokumentId: String): String? {
        logger.info("Henter SED for rinaSakId: $rinaSakId , dokumentId: $dokumentId")

        val exchange: ResponseEntity<String> = euxOAuthRestTemplate.exchange(
            "/buc/$rinaSakId/sed/$dokumentId",
            HttpMethod.GET,
            null,
            String::class.java
        )

    return exchange.body
    }

    internal fun hentBuc(rinaSakId: String): Buc? {
        logger.info("Henter BUC (RinaSakId: $rinaSakId)")

        return euxOAuthRestTemplate.getForObject(
            "/buc/$rinaSakId",
                Buc::class.java)
    }
}
