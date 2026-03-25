package no.nav.eessi.pensjon.klienter.saf

import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.core.io.Resource
import org.springframework.http.*
import org.springframework.retry.RetryCallback
import org.springframework.retry.RetryContext
import org.springframework.retry.RetryListener
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import java.util.Base64

@Component
class SafClient(private val safGraphQlOidcRestTemplate: RestTemplate,
                @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()) {

    private val logger = LoggerFactory.getLogger(SafClient::class.java)

    private var HentDokumentMetadata: MetricsHelper.Metric
    private var HentDokumentInnhold: MetricsHelper.Metric
    private var HentRinaSakIderFraDokumentMetadata: MetricsHelper.Metric

    init {
        HentDokumentMetadata =
            metricsHelper.init("HentDokumentMetadata", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        HentDokumentInnhold = metricsHelper.init(
            "HentDokumentInnhold",
            ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN, HttpStatus.UNAUTHORIZED)
        )
        HentRinaSakIderFraDokumentMetadata =
            metricsHelper.init("HentRinaSakIderFraDokumentMetadata", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
    }

    @Retryable(
        exclude = [HttpClientErrorException.NotFound::class],
        backoff = Backoff(
            delayExpression = "@retrySafConfig.initialRetryMillis",
            delay = 10000L,
            maxDelay = 100000L,
            multiplier = 3.0
        ),
        listeners = ["retrySafLogger"]
    )
    fun hentDokumentMetadata(ident: String, identType: BrukerIdType): HentMetadataResponse {
        logger.info("Henter dokument metadata for aktørid: $ident")

        return HentDokumentMetadata.measure {
            try {
                val headers = HttpHeaders()
                headers.contentType = MediaType.APPLICATION_JSON
                val httpEntity = HttpEntity(genererQueryByIdent(ident, identType), headers)
                val response = safGraphQlOidcRestTemplate.exchange(
                    "/",
                    HttpMethod.POST,
                    httpEntity,
                    String::class.java
                )

                logger.info("Response fra journalføring: ${response.body}")

                mapJsonToAny(response.body!!)

            } catch (ce: HttpClientErrorException) {
                if (ce.statusCode == HttpStatus.FORBIDDEN) {
                    logger.error(
                        "En feil oppstod under henting av dokument metadata fra SAF for aktørID $ident, ikke tilgang",
                        ce
                    )
                    throw HttpClientErrorException(
                        ce.statusCode,
                        "Du har ikke tilgang til dette dokument-temaet. Kontakt nærmeste leder for å få tilgang."
                    )
                }
                logger.error("En feil oppstod under henting av dokument metadata fra SAF: ${ce.responseBodyAsString}")
                throw HttpClientErrorException(
                    ce.statusCode,
                    "En feil oppstod under henting av dokument metadata fra SAF: ${ce.responseBodyAsString}"
                )
            } catch (se: HttpServerErrorException) {
                logger.error(
                    "En feil oppstod under henting av dokument metadata fra SAF: ${se.responseBodyAsString}",
                    se
                )
                throw HttpServerErrorException(
                    se.statusCode,
                    "En feil oppstod under henting av dokument metadata fra SAF: ${se.responseBodyAsString}"
                )
            } catch (ex: Exception) {
                logger.error("En feil oppstod under henting av dokument metadata fra SAF: $ex")
                throw HttpServerErrorException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "En feil oppstod under henting av dokument metadata fra SAF"
                )
            }
        }
    }

    fun hentDokumentInnhold(
        journalpostId: String,
        dokumentInfoId: String,
        variantFormat: String
    ): HentdokumentInnholdResponse {
        return try {
            logger.info("Henter dokumentinnhold for journalpostId: $journalpostId, dokumentInfoId: $dokumentInfoId, variantformat: $variantFormat")
            val variantFormatEnum = VariantFormat.valueOf(variantFormat)

            val path = "/$journalpostId/$dokumentInfoId/$variantFormatEnum"
            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_PDF

            val response = safGraphQlOidcRestTemplate.exchange(
                path,
                HttpMethod.GET,
                HttpEntity("/", headers),
                Resource::class.java
            )

            val filnavn = response.headers.contentDisposition.filename
            val contentType = response.headers.contentType!!.toString()

            val dokumentInnholdBase64 = String(Base64.getEncoder().encode(response.body!!.inputStream.readBytes()))
            HentdokumentInnholdResponse(dokumentInnholdBase64, filnavn!!, contentType)

        } catch (ex: Exception) {
            logger.error("En feil oppstod under henting av dokumentInnhold fra SAF: $ex")
            throw HttpServerErrorException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "En feil oppstod under henting av dokumentinnhold fra SAF"
            )
        }
    }


    @Retryable(
        exclude = [HttpClientErrorException.NotFound::class],
        backoff = Backoff(delayExpression = "@retrySafConfig.initialRetryMillis", delay = 10000L, maxDelay = 100000L, multiplier = 3.0),
        listeners  = ["retrySafLogger"]
    )
    private fun genererQueryByIdent(ident: String, identType: BrukerIdType): String {
        val request = SafRequest(variables = Variables(BrukerId(ident, identType), 10000))
        return request.toJson()
    }
}

@Profile("!retryConfigOverride")
@Component
data class RetrySafConfig(val initialRetryMillis: Long = 20000L)

@Component
class RetrySafLogger : RetryListener {
    private val logger = LoggerFactory.getLogger(RetrySafLogger::class.java)
    override fun <T : Any?, E : Throwable?> onError(context: RetryContext?, callback: RetryCallback<T, E>?, throwable: Throwable?) {
        logger.warn("Feil under henting av data fra SAF - try #${context?.retryCount} - ${throwable?.toString()}", throwable)
    }
}