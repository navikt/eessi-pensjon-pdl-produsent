package no.nav.eessi.pensjon.pdl

import io.micrometer.core.instrument.MeterRegistry
import no.nav.eessi.pensjon.logging.RequestIdHeaderInterceptor
import no.nav.eessi.pensjon.logging.RequestResponseLoggerInterceptor
import no.nav.eessi.pensjon.metrics.RequestCountInterceptor
import no.nav.eessi.pensjon.personoppslag.pdl.PdlTokenCallBack
import no.nav.eessi.pensjon.security.sts.STSService
import no.nav.eessi.pensjon.security.sts.UsernameToOidcInterceptor
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpRequest
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Component
class PersonMottakRestTemplate(
    private val registry: MeterRegistry,
    private val stsService: STSService
) {

    @Value("\${PDL_PERSON_MOTTAK_URL}")
    lateinit var url: String

    @Bean
    fun personMottakUsernameOidcRestTemplate(templateBuilder: RestTemplateBuilder, pdlTokenComponent: PdlTokenCallBack): RestTemplate {
        return templateBuilder
            .rootUri(url)
            .errorHandler(DefaultResponseErrorHandler())
            .setReadTimeout(Duration.ofSeconds(120))
            .setConnectTimeout(Duration.ofSeconds(120))
            .additionalInterceptors(
                RequestIdHeaderInterceptor(),
                RequestCountInterceptor(registry),
                RequestResponseLoggerInterceptor(),
                UsernameToOidcInterceptor(stsService),
                PdlInterceptor(pdlTokenComponent))

            .build().apply {
                requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
            }
    }


    internal class PdlInterceptor(private val pdlTokens: PdlTokenCallBack) : ClientHttpRequestInterceptor {

        private val logger = LoggerFactory.getLogger(PdlInterceptor::class.java)

        override fun intercept(request: HttpRequest,
                               body: ByteArray,
                               execution: ClientHttpRequestExecution
        ): ClientHttpResponse {

            val token = pdlTokens.callBack()
            logger.debug("tokenIntercetorRequest: userToken: ${token.isUserToken}")
            // [System]
            request.headers["Nav-Consumer-Token"] = "Bearer ${token.systemToken}"
            return execution.execute(request, body)
        }
    }

}
