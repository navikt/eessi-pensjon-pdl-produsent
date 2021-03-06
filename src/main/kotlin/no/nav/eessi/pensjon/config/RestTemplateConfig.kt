package no.nav.eessi.pensjon.config

import no.nav.eessi.pensjon.logging.RequestIdHeaderInterceptor
import no.nav.security.token.support.client.core.ClientProperties
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.RestTemplate


@Configuration
@Profile("prod", "test")
class RestTemplateConfig(
    private val clientConfigurationProperties: ClientConfigurationProperties,
    private val oAuth2AccessTokenService: OAuth2AccessTokenService?) {

    @Value("\${EUX_RINA_API_V1_URL}")
    lateinit var euxUrl: String

    @Value("\${EESSI_PEN_ONPREM_PROXY_URL}")
    lateinit var proxyUrl: String

    @Bean
    fun euxOAuthRestTemplate(): RestTemplate = opprettRestTemplate(euxUrl, "eux-credentials")

    @Bean
    fun proxyOAuthRestTemplate(): RestTemplate = opprettRestTemplate(proxyUrl, "proxy-credentials")

    @Bean
    fun personMottakRestTemplate(): RestTemplate = opprettRestTemplate(proxyUrl, "proxy-credentials")


    private fun opprettRestTemplate(url: String, oAuthKey: String) : RestTemplate {
        return RestTemplateBuilder()
            .rootUri(url)
            .errorHandler(DefaultResponseErrorHandler())
            .additionalInterceptors(
                RequestIdHeaderInterceptor(),
                bearerTokenInterceptor(clientProperties(oAuthKey), oAuth2AccessTokenService!!)
            )
            .build().apply {
                requestFactory = HttpComponentsClientHttpRequestFactory()
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

