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
import org.springframework.web.client.exchange
import java.util.Base64

@Component
class SafClient(private val safGraphQlOidcRestTemplate: RestTemplate,
                @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()) {

    private val logger = LoggerFactory.getLogger(SafClient::class.java)

    private var hentDokumentMetadata: MetricsHelper.Metric
    private var hentDokumentInnhold: MetricsHelper.Metric
    private var hentRinaSakIderFraDokumentMetadata: MetricsHelper.Metric

    init {
        hentDokumentMetadata = metricsHelper.init("HentDokumentMetadata", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
        hentDokumentInnhold = metricsHelper.init("HentDokumentInnhold", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN, HttpStatus.UNAUTHORIZED))
        hentRinaSakIderFraDokumentMetadata = metricsHelper.init("HentRinaSakIderFraDokumentMetadata", ignoreHttpCodes = listOf(HttpStatus.FORBIDDEN))
    }

    fun hentDokumentMetadata(ident: String, identType: BrukerIdType): HentMetadataResponse {
        logger.info("Henter dokument metadata for aktørid: $ident")

        return hentDokumentMetadata.measure {
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

            val path = "/$journalpostId/$dokumentInfoId/${VariantFormat.valueOf(variantFormat)}"

            val entity = HttpEntity("/", HttpHeaders().apply {
                this.contentType = MediaType.APPLICATION_PDF
            })

            val response = safGraphQlOidcRestTemplate.exchange<Resource>(
                path,
                HttpMethod.GET,
                entity
            )

            val filename = response.headers.contentDisposition.filename!!
            val contentType = response.headers.contentType!!.toString()

            val documentBytes = response.body!!.inputStream.readBytes()
            val documentBase64 = Base64.getEncoder().encodeToString(documentBytes)

            HentdokumentInnholdResponse(documentBase64, filename, contentType)

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