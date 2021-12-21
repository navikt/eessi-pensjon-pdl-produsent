package no.nav.eessi.pensjon.pdl.integrationtest

import org.mockserver.client.MockServerClient
import org.mockserver.model.Header
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse
import org.mockserver.model.HttpStatusCode
import org.springframework.http.HttpMethod
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

class CustomMockServer() {
    private val serverPort = CompletableFuture.completedFuture(System.getProperty("mockserverport").toInt())

    fun mockSTSToken() = apply {
        MockServerClient(serverPort).`when`(
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

    fun medSed(bucPath: String, bucLocation: String) = apply {
        MockServerClient(serverPort).`when`(
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

        MockServerClient(serverPort).`when`(
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
        MockServerClient(serverPort).`when`(
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
        MockServerClient(serverPort).`when`(
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
        println("CustomMockServer Port: ${serverPort.get().toString()}")
    }

    fun medKodeverk(kodeverkPath: String, kodeVerkLocation: String) = apply {

        MockServerClient(serverPort).`when`(
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