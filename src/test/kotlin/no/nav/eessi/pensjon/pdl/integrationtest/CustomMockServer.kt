package no.nav.eessi.pensjon.pdl.integrationtest

import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.utils.toJson
import org.mockserver.client.MockServerClient
import org.mockserver.model.Header
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse
import org.mockserver.model.HttpStatusCode
import org.mockserver.verify.VerificationTimes
import org.springframework.http.HttpMethod
import java.nio.file.Files
import java.nio.file.Paths

class CustomMockServer() {
    private val serverPort = System.getProperty("mockserverport").toInt()

    fun medSed(bucPath: String, sedLocation: String) = apply {
        MockServerClient("localhost", serverPort).`when`(
            request()
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
            request()
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
            request()
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

    fun medMockBuc(bucPath: String, mockBuc: String) = apply {
        MockServerClient("localhost", serverPort).`when`(
            request()
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

    fun verifyRequest(path: String, times: Int) = apply {
        MockServerClient("localhost", serverPort)
            .verify(
                request()
                    .withPath(path),
                VerificationTimes.atLeast(times)
            )
    }

    fun verifyRequestWithBody(path: String, body: String) = apply {
        MockServerClient("localhost", serverPort)
            .verify(
                request()
                    .withMethod(HttpMethod.POST.name)
                    .withPath(path)
                    .withBody(body.trimIndent())
                ,
                VerificationTimes.atLeast(1)
            )
    }

    companion object {
        fun mockBuc(bucId: String, bucType: BucType, docIder: List<ForenkletSED>): String {
            return """
            {
              "id": "$bucId",
              "processDefinitionName": "${bucType.name}",
              "documents": ${docIder.toJson()}
              
            } 
          
        """.trimIndent()
        }
    }
}