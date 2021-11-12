package no.nav.eessi.pensjon.config

import io.micrometer.core.instrument.MeterRegistry
import no.nav.eessi.pensjon.logging.RequestIdHeaderInterceptor
import no.nav.eessi.pensjon.logging.RequestResponseLoggerInterceptor
import no.nav.eessi.pensjon.metrics.RequestCountInterceptor
import no.nav.eessi.pensjon.security.sts.STSService
import no.nav.eessi.pensjon.security.sts.UsernameToOidcInterceptor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpRequest
import org.springframework.http.MediaType
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.RestTemplate
import java.util.*

@Configuration
class RestTemplateConfig(private val securityTokenExchangeService: STSService, private val meterRegistry: MeterRegistry) {

    @Value("\${NORG2_URL}")
    lateinit var norg2Url: String

    @Bean
    fun norg2OidcRestTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
        return templateBuilder
                .rootUri(norg2Url)
                .errorHandler(DefaultResponseErrorHandler())
                .additionalInterceptors(
                        RequestIdHeaderInterceptor(),
                        RequestResponseLoggerInterceptor(),
                        RequestCountInterceptor(meterRegistry),
                        UsernameToOidcInterceptor(securityTokenExchangeService))
                .build().apply {
                    requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
                }
    }

    class RequestInterceptor : ClientHttpRequestInterceptor {
        override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {
            request.headers["X-Correlation-ID"] = UUID.randomUUID().toString()
            request.headers["Content-Type"] = MediaType.APPLICATION_JSON.toString()
            return execution.execute(request, body)
        }
    }
}



class FullRequestResponseLoggerInterceptor : ClientHttpRequestInterceptor {
    private val log: Logger by lazy { LoggerFactory.getLogger(RequestResponseLoggerInterceptor::class.java) }

    override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {

        logRequest(request, body)
        val response: ClientHttpResponse = execution.execute(request, body)
        logResponse(response)
        return response
    }

    private fun logRequest(request: HttpRequest, body: ByteArray) {
        if (log.isDebugEnabled) {
            val requestLog = StringBuffer()

            requestLog.append("\n===========================request begin================================================")
            requestLog.append("\nURI            :  ${request.uri}")
            requestLog.append("\nMethod         :  ${request.method}")
            requestLog.append("\nHeaders        :  ${request.headers}")
            requestLog.append("\nComplete body  :  ${String(body)}")
            requestLog.append("\n==========================request end================================================")
            log.debug(requestLog.toString())
        }
    }

    private fun logResponse(response: ClientHttpResponse) {
        if (log.isDebugEnabled) {
            val responseLog = StringBuilder()

            responseLog.append("\n===========================response begin================================================")
            responseLog.append("\nStatus code    : ${response.statusCode}")
            responseLog.append("\nStatus text    : ${response.statusText}")
            responseLog.append("\nHeaders        : ${response.headers}")
            responseLog.append("\nComplete body  :  ${String(response.body.readBytes())}")
            responseLog.append("\n==========================response end================================================")
            log.debug(responseLog.toString())
        }
    }
}

