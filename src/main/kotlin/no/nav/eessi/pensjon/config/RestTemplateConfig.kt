package no.nav.eessi.pensjon.config

import io.micrometer.core.instrument.MeterRegistry
import no.nav.common.token_client.builder.AzureAdTokenClientBuilder
import no.nav.common.token_client.client.AzureAdOnBehalfOfTokenClient
import no.nav.eessi.pensjon.eux.klient.EuxKlientLib
import no.nav.eessi.pensjon.logging.RequestIdHeaderInterceptor
import no.nav.eessi.pensjon.logging.RequestResponseLoggerInterceptor
import no.nav.eessi.pensjon.metrics.RequestCountInterceptor
import no.nav.eessi.pensjon.shared.retry.IOExceptionRetryInterceptor
import no.nav.security.token.support.client.core.ClientProperties
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import no.nav.security.token.support.core.context.TokenValidationContextHolder
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
import org.springframework.web.client.RestTemplate


@Configuration
@Profile("prod", "test")
class RestTemplateConfig(
    private val clientConfigurationProperties: ClientConfigurationProperties,
    private val oAuth2AccessTokenService: OAuth2AccessTokenService?,
    private val tokenValidationContextHolder: TokenValidationContextHolder,
    private val meterRegistry: MeterRegistry,
        ) {

    @Value("\${EUX_RINA_API_V1_URL}")
    lateinit var euxUrl: String

    @Value("\${PDL_MOTTAK_URL}")
    lateinit var pdlMottakUrl: String

    @Value("\${NORG2_URL}")
    lateinit var norg2Url: String

    @Value("\${AZURE_APP_SAF_CLIENT_ID}")
    lateinit var safClientId: String

    @Value("\${SAF_GRAPHQL_URL}")
    lateinit var graphQlUrl: String

    @Value("\${SAF_HENTDOKUMENT_URL}")
    lateinit var hentRestUrl: String

    private val logger = LoggerFactory.getLogger(RestTemplateConfig::class.java)

    @Bean
    fun euxOAuthRestTemplate(): RestTemplate = restTemplate(euxUrl, bearerTokenInterceptor(clientProperties("eux-credentials"), oAuth2AccessTokenService!!))

    @Bean
    fun norg2RestTemplate(): RestTemplate = buildRestTemplate(norg2Url)

    @Bean
    fun personMottakRestTemplate(): RestTemplate = restTemplate(pdlMottakUrl, bearerTokenInterceptor(clientProperties("pdl-mottak-credentials"), oAuth2AccessTokenService!!))

    @Bean
    fun euxKlient(): EuxKlientLib = EuxKlientLib(euxOAuthRestTemplate())

    @Bean
    fun safGraphQlOidcRestTemplate() = restTemplate(graphQlUrl, bearerTokenInterceptor(clientProperties("saf-credentials"), oAuth2AccessTokenService!!))

    @Bean
    fun safRestOidcRestTemplate() = restTemplate(hentRestUrl, onBehalfOfBearerTokenInterceptor(safClientId))


    private fun restTemplate(url: String, tokenIntercetor: ClientHttpRequestInterceptor?) : RestTemplate {
        logger.info("init restTemplate: $url")
        return RestTemplateBuilder()
            .rootUri(url)
            .errorHandler(DefaultResponseErrorHandler())
            .additionalInterceptors(
                RequestIdHeaderInterceptor(),
                IOExceptionRetryInterceptor(),
                RequestCountInterceptor(meterRegistry),
                RequestResponseLoggerInterceptor(),
                tokenIntercetor
            )
            .build().apply {
                requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())
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

    private fun onBehalfOfBearerTokenInterceptor(clientId: String): ClientHttpRequestInterceptor {
        logger.info("init onBehalfOfBearerTokenInterceptor: $clientId")
        return ClientHttpRequestInterceptor { request: HttpRequest, body: ByteArray?, execution: ClientHttpRequestExecution ->
            val navidentTokenFromUI = getToken(tokenValidationContextHolder).tokenAsString

            logger.info("NAVIdent: ${getClaims(tokenValidationContextHolder).get("NAVident")?.toString()}")

            val tokenClient: AzureAdOnBehalfOfTokenClient = AzureAdTokenClientBuilder.builder()
                .withNaisDefaults()
                .buildOnBehalfOfTokenClient()

            val accessToken: String = tokenClient.exchangeOnBehalfOfToken(
                "api://$clientId/.default",
                navidentTokenFromUI
            )

            request.headers.setBearerAuth(accessToken)
            execution.execute(request, body!!)
        }
    }

    private fun clientProperties(oAuthKey: String): ClientProperties = clientConfigurationProperties.registration[oAuthKey]
        ?: throw RuntimeException("could not find oauth2 client config for $oAuthKey")

    private fun bearerTokenInterceptor(
        clientProperties: ClientProperties,
        oAuth2AccessTokenService: OAuth2AccessTokenService
    ): ClientHttpRequestInterceptor {
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

