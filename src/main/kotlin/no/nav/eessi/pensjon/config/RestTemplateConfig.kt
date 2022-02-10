package no.nav.eessi.pensjon.config

import no.nav.eessi.pensjon.logging.RequestIdHeaderInterceptor
import no.nav.eessi.pensjon.logging.RequestResponseLoggerInterceptor
import no.nav.eessi.pensjon.personoppslag.pdl.PdlTokenCallBack
import no.nav.eessi.pensjon.security.sts.STSService
import no.nav.eessi.pensjon.security.sts.UsernameToOidcInterceptor
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpRequest
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Configuration
class RestTemplateConfig(
    private val securityTokenExchangeService: STSService,
    @param:Value("\${norg2_url}") val norg2Url: String,
    @param:Value("\${kodeverk_rest_api_url}") val kodeverkUrl: String,
    @param:Value("\${PDL_PERSON_MOTTAK_URL}") val pdlInnsendingUrl: String,
    @param:Value("\${EUX_RINA_API_V1_URL}") val euxUrl: String) {

    @Bean
    fun euxUsernameOidcRestTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
        return templateBuilder
            .rootUri(euxUrl)
            .errorHandler(DefaultResponseErrorHandler())
            .setReadTimeout(Duration.ofSeconds(120))
            .setConnectTimeout(Duration.ofSeconds(120))
            .additionalInterceptors(
                RequestIdHeaderInterceptor(),
                RequestResponseLoggerInterceptor(),
                UsernameToOidcInterceptor(securityTokenExchangeService))
            .build().apply {
                requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
            }
    }

    @Bean
    fun norg2OidcRestTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
        return templateBuilder
                .rootUri(norg2Url)
                .errorHandler(DefaultResponseErrorHandler())
                .additionalInterceptors(
                        RequestIdHeaderInterceptor(),
                        RequestResponseLoggerInterceptor(),
                        UsernameToOidcInterceptor(securityTokenExchangeService))
                .build().apply {
                    requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
                }
    }

    @Bean
    fun kodeRestTemplate(templateBuilder: RestTemplateBuilder): RestTemplate {
        return templateBuilder
            .rootUri(kodeverkUrl)
            .errorHandler(DefaultResponseErrorHandler())
            .additionalInterceptors(
                RequestIdHeaderInterceptor()
            )
            .build()
    }


    @Bean
    fun personMottakUsernameOidcRestTemplate(templateBuilder: RestTemplateBuilder, pdlTokenComponent: PdlTokenCallBack): RestTemplate {
        return templateBuilder
            .rootUri(pdlInnsendingUrl)
            .errorHandler(DefaultResponseErrorHandler())
            .setReadTimeout(Duration.ofSeconds(120))
            .setConnectTimeout(Duration.ofSeconds(120))
            .additionalInterceptors(
                RequestIdHeaderInterceptor(),
                RequestResponseLoggerInterceptor(),
                UsernameToOidcInterceptor(securityTokenExchangeService),
                PdlInterceptor(pdlTokenComponent))
            .build().apply {
                requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
            }
    }

    internal class PdlInterceptor(private val pdlTokens: PdlTokenCallBack) : ClientHttpRequestInterceptor {
        private val logger = LoggerFactory.getLogger(PdlInterceptor::class.java)
        override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {
            val token = pdlTokens.callBack()
            logger.debug("tokenIntercetorRequest: userToken: ${token.isUserToken}")
            // [System]
            request.headers["Nav-Consumer-Token"] = "Bearer ${token.systemToken}"
            return execution.execute(request, body)
        }
    }


}

