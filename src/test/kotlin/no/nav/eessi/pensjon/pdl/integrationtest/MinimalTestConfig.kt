package no.nav.eessi.pensjon.pdl.integrationtest

import io.mockk.mockk
import no.nav.eessi.pensjon.eux.klient.EuxKlientLib
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.shared.retry.IOExceptionRetryInterceptor
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier
import org.apache.hc.core5.ssl.SSLContextBuilder
import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.RestTemplate

@TestConfiguration
class MinimalTestConfig {
    @Bean
    fun personMottakRestTemplate(): RestTemplate {
        return mockedRestTemplate()
    }

    @Bean
    fun gcpStorageService(): GcpStorageService {
        return mockk()
    }


    @Bean
    fun euxOAuthRestTemplate(): RestTemplate? {
        return opprettSSLRestTemplate()
    }

    @Bean
    fun norg2RestTemplate(): RestTemplate? {
        return opprettSTSRestTemplate()
    }

    @Bean
    fun euxKlientLib(): EuxKlientLib = EuxKlientLib(euxOAuthRestTemplate()!!)

    @Bean
    fun opprettSSLRestTemplate(): RestTemplate {
        val sslContext = SSLContextBuilder.create()
            .loadTrustMaterial(null) { _, _ -> true } // Trust all certificates
            .build()

        val connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
            .setTlsSocketStrategy(
                DefaultClientTlsStrategy(
                    sslContext,
                    HostnameVerificationPolicy.CLIENT,
                    NoopHostnameVerifier.INSTANCE
                )
            ).build()

        val httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .build()

        val customRequestFactory = HttpComponentsClientHttpRequestFactory()
        customRequestFactory.httpClient = httpClient

        return RestTemplateBuilder()
            .rootUri("https://localhost:${System.getProperty("mockserverport")}")
            .build().apply {
                requestFactory = customRequestFactory
            }
    }

    @Bean
    fun opprettSTSRestTemplate(): RestTemplate {
        return RestTemplateBuilder()
            .additionalInterceptors(IOExceptionRetryInterceptor())
            .build()
    }

    private fun mockedRestTemplate(): RestTemplate {
        val port = System.getProperty("mockserverport")
        return RestTemplateBuilder()
            .rootUri("http://localhost:${port}")
            .build()
    }
}