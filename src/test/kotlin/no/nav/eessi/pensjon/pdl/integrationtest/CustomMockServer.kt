package no.nav.eessi.pensjon.pdl.integrationtest

import org.mockserver.client.MockServerClient
import org.mockserver.model.Header
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse
import org.mockserver.model.HttpStatusCode
import org.springframework.http.HttpMethod
import java.nio.file.Files
import java.nio.file.Paths

class CustomMockServer() {
    private val serverPort = System.getProperty("mockserverport").toInt()

    fun mockSTSToken() = apply {
        MockServerClient("localhost", serverPort).`when`(
            HttpRequest.request()
                .withMethod(HttpMethod.GET.name)
                .withQueryStringParameter("grant_type", "client_credentials")
        )
            .respond(
                HttpResponse.response()
                    .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                    .withStatusCode(HttpStatusCode.OK_200.code())
                    .withBody(String(Files.readAllBytes(Paths.get("src/test/resources/STStoken.json"))))
            )
    }

    fun medSed(bucPath: String, sedLocation: String) = apply {
        MockServerClient("localhost", serverPort).`when`(
            HttpRequest.request()
                .withMethod(HttpMethod.GET.name)
                .withPath(bucPath)
        )
            .respond(
                HttpResponse.response()
                    .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                    .withStatusCode(HttpStatusCode.OK_200.code())
                    .withBody(String(Files.readAllBytes(Paths.get(sedLocation))))
            )
    }

    fun medMockSed(bucPath: String, mockSed: String) = apply {
        MockServerClient("localhost", serverPort).`when`(
            HttpRequest.request()
                .withMethod(HttpMethod.GET.name)
                .withPath(bucPath)
        )
            .respond(
                HttpResponse.response()
                    .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                    .withStatusCode(HttpStatusCode.OK_200.code())
                    .withBody(mockSed)
            )
    }

    fun medEndring() = apply {
        MockServerClient("localhost", serverPort).`when`(
            HttpRequest.request()
                .withMethod(HttpMethod.POST.name)
                .withPath("/api/v1/endringer")
        )
            .respond(
                HttpResponse.response()
                    .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                    .withStatusCode(HttpStatusCode.OK_200.code())
                    .withBody("{}")
            )
    }

    fun medbBuc(bucPath: String, bucLocation: String) = apply {
        MockServerClient("localhost", serverPort).`when`(
            HttpRequest.request()
                .withMethod(HttpMethod.GET.name)
                .withPath(bucPath)
        )
            .respond(
                HttpResponse.response()
                    .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                    .withStatusCode(HttpStatusCode.OK_200.code())
                    .withBody(String(Files.readAllBytes(Paths.get(bucLocation))))
            )
    }

    fun medMockBuc(bucPath: String, mockBuc: String) = apply {
        MockServerClient("localhost", serverPort).`when`(
            HttpRequest.request()
                .withMethod(HttpMethod.GET.name)
                .withPath(bucPath)
        )
            .respond(
                HttpResponse.response()
                    .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                    .withStatusCode(HttpStatusCode.OK_200.code())
                    .withBody(mockBuc)
            )
    }.also {
        println("CustomMockServer Port: $serverPort")
    }

    fun medKodeverk(kodeverkPath: String, kodeVerkLocation: String) = apply {

        MockServerClient("localhost", serverPort).`when`(
            HttpRequest.request()
                .withMethod(HttpMethod.GET.name)
                .withPath(kodeverkPath)
        )
            .respond(
                HttpResponse.response()
                    .withHeader(Header("Content-Type", "application/json; charset=utf-8"))
                    .withStatusCode(HttpStatusCode.OK_200.code())
                    .withBody(String(Files.readAllBytes(Paths.get(kodeVerkLocation))))
            )
    }
}