package no.nav.eessi.pensjon.config

import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import io.micrometer.core.instrument.MeterRegistry
import no.nav.eessi.pensjon.eux.klient.EuxKlientLib
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.logging.RequestIdHeaderInterceptor
import no.nav.eessi.pensjon.logging.RequestResponseLoggerInterceptor
import no.nav.eessi.pensjon.metrics.RequestCountInterceptor
import no.nav.eessi.pensjon.shared.retry.IOExceptionRetryInterceptor
import no.nav.security.token.support.client.core.ClientProperties
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpRequest
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate
import java.time.Duration


@Configuration
@Profile("prod", "test")
class RestTemplateConfig(
        private val clientConfigurationProperties: ClientConfigurationProperties,
        private val oAuth2AccessTokenService: OAuth2AccessTokenService?,
        private val meterRegistry: MeterRegistry,
        ) {

    private val logger = LoggerFactory.getLogger(RestTemplateConfig::class.java)

    @Value("\${EUX_RINA_API_V1_URL}")
    lateinit var euxUrl: String

    @Value("\${PDL_MOTTAK_URL}")
    lateinit var pdlMottakUrl: String

    @Value("\${NORG2_URL}")
    lateinit var norg2Url: String

    @Value("\${PENSJONSINFORMASJON_URL}")
    lateinit var pensjonUrl: String

    @Bean
    fun euxOAuthRestTemplate(): RestTemplate = opprettRestTemplate(euxUrl, "eux-credentials")

    @Bean
    fun norg2RestTemplate(): RestTemplate = buildRestTemplate(norg2Url)

    @Bean
    fun personMottakRestTemplate(): RestTemplate = opprettRestTemplate(pdlMottakUrl, "pdl-mottak-credentials")

    @Bean
    fun pensjoninformasjonRestTemplate() = restTemplate(pensjonUrl, oAuth2BearerTokenInterceptor(clientProperties("proxy-credentials"), oAuth2AccessTokenService!!))

    @Bean
    fun euxKlient(): EuxKlientLib = EuxKlientLib(euxOAuthRestTemplate())

    private fun restTemplate(url: String, tokenIntercetor: ClientHttpRequestInterceptor?, defaultErrorHandler: ResponseErrorHandler = DefaultResponseErrorHandler()) : RestTemplate {
        logger.info("init restTemplate: $url")
        return RestTemplateBuilder()
            .rootUri(url)
            .errorHandler(defaultErrorHandler)
            .setReadTimeout(Duration.ofSeconds(120))
            .setConnectTimeout(Duration.ofSeconds(120))
            .additionalInterceptors(
                RequestIdHeaderInterceptor(),
                IOExceptionRetryInterceptor(),
                RequestCountInterceptor(meterRegistry),
                RequestResponseLoggerInterceptor(),
                tokenIntercetor
            )
            .build().apply {
                requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory()
                    .apply { setOutputStreaming(false) }
                )
            }
    }

    private fun opprettRestTemplate(url: String, oAuthKey: String) : RestTemplate {
        return RestTemplateBuilder()
            .rootUri(url)
            .errorHandler(DefaultResponseErrorHandler())
            .additionalInterceptors(
                RequestIdHeaderInterceptor(),
                IOExceptionRetryInterceptor(),
                RequestCountInterceptor(meterRegistry),
                RequestResponseLoggerInterceptor(),
                bearerTokenInterceptor(clientProperties(oAuthKey), oAuth2AccessTokenService!!)
            )
            .build().apply {
                requestFactory = BufferingClientHttpRequestFactory(
                    SimpleClientHttpRequestFactory()
                    .apply { setOutputStreaming(false) }
                )
            }
    }

    private fun buildRestTemplate(url: String): RestTemplate {
        return RestTemplateBuilder()
            .rootUri(url)
            .errorHandler(DefaultResponseErrorHandler())
            .additionalInterceptors(
                RequestIdHeaderInterceptor(),
                RequestResponseLoggerInterceptor()
            )
            .build().apply {
                requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
            }

    }

    private fun oAuth2BearerTokenInterceptor(
        clientProperties: ClientProperties,
        oAuth2AccessTokenService: OAuth2AccessTokenService
    ): ClientHttpRequestInterceptor {
        return ClientHttpRequestInterceptor { request: HttpRequest, body: ByteArray?, execution: ClientHttpRequestExecution ->
            val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
            request.headers.setBearerAuth(response.accessToken)
            execution.execute(request, body!!)
        }
    }

    private fun clientProperties(oAuthKey: String): ClientProperties = clientConfigurationProperties.registration[oAuthKey] ?: throw RuntimeException("could not find oauth2 client config for $oAuthKey")

    private fun bearerTokenInterceptor(
        clientProperties: ClientProperties,
        oAuth2AccessTokenService: OAuth2AccessTokenService
    ): ClientHttpRequestInterceptor? {
        return ClientHttpRequestInterceptor { request: HttpRequest, body: ByteArray?, execution: ClientHttpRequestExecution ->
            val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
            request.headers.setBearerAuth(response.accessToken)
            /*
            val tokenChunks = response.accessToken.split(".")
            val tokenBody =  tokenChunks[1]
            logger.info("subject: " + JWTClaimsSet.parse(Base64.getDecoder().decode(tokenBody).decodeToString()).subject)
            */
            execution.execute(request, body!!)
        }
    }

}

