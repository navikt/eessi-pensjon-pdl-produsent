package no.nav.eessi.pensjon.pdl.dodsmelding

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import no.nav.eessi.pensjon.klienter.saf.BrukerIdType
import no.nav.eessi.pensjon.klienter.saf.HentdokumentInnholdResponse
import no.nav.eessi.pensjon.klienter.saf.SafClient
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.Ident
import no.nav.eessi.pensjon.OpprettH070.OpprettH070
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.person.pdl.leesah.Personhendelse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.exchange

@Disabled
class DodsmeldingBehandlerTest {

    private val safGraphQlOidcRestTemplate: RestTemplate = mockk(relaxed = true)
    private val hentRestUrlRestTemplate: RestTemplate = mockk(relaxed = true)
    private val safClient: SafClient = spyk(SafClient(safGraphQlOidcRestTemplate, hentRestUrlRestTemplate))
    private val personService = mockk<PersonService>()
    private val opprettH070 = mockk<OpprettH070>()
    private val fagmodulKlient = mockk<FagmodulKlient>()
    private val euxService = mockk<EuxService>()

    private lateinit var dodsmeldingBehandler: DodsmeldingBehandler

    @BeforeEach
    fun setup() {
        dodsmeldingBehandler = DodsmeldingBehandler(fagmodulKlient, safClient, personService, opprettH070, euxService, mockk())
    }

    @Test
    fun `behandle returnerer tidlig naar personhendelse har tom liste med identer`() {
        val personhendelse = mockk<Personhendelse> {
            every { personidenter } returns emptyList()
        }

        dodsmeldingBehandler.behandle(personhendelse)

        verify(exactly = 0) { personService.hentPerson(any()) }
        verify(exactly = 0) { safClient.hentDokumentMetadata(any(), any()) }
    }

    @Test
    fun `behandle henter ikke dokumentmetadata naar person ikke har utenlandskIdentifikasjonsnummer`() {
        val personhendelse = mockk<Personhendelse> {
            every { personidenter } returns listOf("12345678901")
        }
        val ident = Ident.bestemIdent("12345678901")
        every { personService.hentPerson(ident) } returns mockk {
            every { utenlandskIdentifikasjonsnummer } returns emptyList()
        }

        dodsmeldingBehandler.behandle(personhendelse)

        verify(exactly = 1) { personService.hentPerson(ident) }
        verify(exactly = 0) { safClient.hentDokumentMetadata(any(), any()) }
    }

    @Test
    fun `behandle henter ikke dokumentmetadata naar person er null`() {
        val personhendelse = mockk<Personhendelse> {
            every { personidenter } returns listOf("12345678901")
        }
        val ident = Ident.bestemIdent("12345678901")
        every { personService.hentPerson(ident) } returns null

        dodsmeldingBehandler.behandle(personhendelse)

        verify(exactly = 1) { personService.hentPerson(ident) }
        verify(exactly = 0) { safClient.hentDokumentMetadata(any(), any()) }
    }

    @Test
    fun `behandle henter ikke dokumentmetadata naar utstederland ikke er i gyldigeUtstederland`() {
        val personhendelse = mockk<Personhendelse> {
            every { personidenter } returns listOf("12345678901")
        }
        val ident = Ident.bestemIdent("12345678901")
        every { personService.hentPerson(ident) } returns mockk {
            every { utenlandskIdentifikasjonsnummer } returns listOf(
                mockk { every { utstederland } returns "DEU" }
            )
        }

        dodsmeldingBehandler.behandle(personhendelse)

        verify(exactly = 1) { personService.hentPerson(ident) }
        verify(exactly = 0) { safClient.hentDokumentMetadata(any(), any()) }
    }

    @ParameterizedTest
    @CsvSource("SWE", "FIN", "POL")
    fun `behandle henter dokumentmetadata naar utstederland er `(land: String) {
        val personhendelse = mockk<Personhendelse> {
            every { personidenter } returns listOf("12345678901")
        }
        val ident = Ident.bestemIdent("12345678901")
        every { personService.hentPerson(ident) } returns mockk {
            every { utenlandskIdentifikasjonsnummer } returns listOf(
                mockk { every { utstederland } returns land }
            )
        }
        every { safClient.hentDokumentMetadata("12345678901", BrukerIdType.FNR) } returns mockk {
            every { data } returns mockk {
                every { dokumentoversiktBruker } returns mockk {
                    every { journalposter } returns emptyList()
                }
            }
        }

        dodsmeldingBehandler.behandle(personhendelse)

        verify(exactly = 1) { safClient.hentDokumentMetadata("12345678901", BrukerIdType.FNR) }
    }

    @Test
    fun `behandle henter dokumentmetadata skal fungere uten tema`() {
        val personhendelse = mockk<Personhendelse> {
            every { personidenter } returns listOf("12345678901")
        }
        val ident = Ident.bestemIdent("12345678901")
        every { personService.hentPerson(ident) } returns mockk {
            every { utenlandskIdentifikasjonsnummer } returns listOf(
                mockk { every { utstederland } returns "SWE" }
            )
        }

        every {
            safGraphQlOidcRestTemplate.exchange("/", HttpMethod.POST, any(), String::class.java)
        } returns ResponseEntity(
            """
                {
                  "data": {
                    "dokumentoversiktBruker": {
                      "journalposter": [
                        {
                          "tilleggsopplysninger": [
                            {
                              "nokkel": "eessi_pensjon_bucid",
                              "verdi": "1455350"
                            }
                          ],
                          "journalpostId": "454102392",
                          "datoOpprettet": "2026-03-25T11:15:48",
                          "tittel": "Inngående P6000 - Melding om vedtak",
                          "journalfoerendeEnhet": "4476",
                          "behandlingstema": "ab0254",
                          "dokumenter": [
                            {
                              "dokumentInfoId": "454528669",
                              "tittel": "P6000 - Melding om vedtak.pdf",
                              "dokumentvarianter": [
                                {
                                  "filnavn": null,
                                  "variantformat": "ARKIV"
                                }
                              ]
                            }
                          ]
                        }
                      ]
                    }
                  }
                }
            """.trimIndent(),
            org.springframework.http.HttpStatus.OK
        )

        val dummyResource: Resource = ByteArrayResource("dummy".toByteArray())
        every {
            hentRestUrlRestTemplate.exchange<Resource>(
                any<String>(),
                HttpMethod.GET,
                any()
            )
        } returns ResponseEntity(dummyResource, org.springframework.http.HttpStatus.OK)

        every { opprettH070.oppretterH070(any(), any()) } returns mockk(relaxed = true)

        dodsmeldingBehandler.behandle(personhendelse)

        verify(exactly = 1) { safClient.hentDokumentMetadata("12345678901", BrukerIdType.FNR) }
    }

    @Test
    fun `behandle henter dokumentmetadata naar minst ett utstederland er gyldig blant flere`() {
        val personhendelse = mockk<Personhendelse> {
            every { personidenter } returns listOf("12345678901")
        }
        val ident = Ident.bestemIdent("12345678901")
        every { personService.hentPerson(ident) } returns mockk {
            every { utenlandskIdentifikasjonsnummer } returns listOf(
                mockk { every { utstederland } returns "DEU" },
                mockk { every { utstederland } returns "SWE" }
            )
        }
        every { safClient.hentDokumentMetadata("12345678901", BrukerIdType.FNR) } returns mockk {
            every { data } returns mockk {
                every { dokumentoversiktBruker } returns mockk {
                    every { journalposter } returns emptyList()
                }
            }
        }

        dodsmeldingBehandler.behandle(personhendelse)

        verify(exactly = 1) { safClient.hentDokumentMetadata("12345678901", BrukerIdType.FNR) }
    }

    @Test
    fun `behandle henter dokumentinnhold for journalposter med dokumenter`() {
        val personhendelse = mockk<Personhendelse> {
            every { personidenter } returns listOf("12345678901")
        }
        val ident = Ident.bestemIdent("12345678901")
        every { personService.hentPerson(ident) } returns mockk {
            every { utenlandskIdentifikasjonsnummer } returns listOf(
                mockk { every { utstederland } returns "SWE" }
            )
        }
        every { safClient.hentDokumentMetadata("12345678901", BrukerIdType.FNR) } returns mockk {
            every { data } returns mockk {
                every { dokumentoversiktBruker } returns mockk {
                    every { journalposter } returns listOf(
                        mockk {
                            every { journalpostId } returns "123456"
                            every { datoOpprettet } returns "2026-01-01"
                            every { tittel } returns "Test dokument"
                            every { tilleggsopplysninger } returns emptyList()
                            every { dokumenter } returns listOf(
                                mockk { every { dokumentInfoId } returns "dok123" }
                            )
                        }
                    )
                }
            }
        }

        every { safClient.hentDokumentInnhold(any(), any(), any()) } returns HentdokumentInnholdResponse(
            filInnhold = "",
            fileName = "test.pdf",
            contentType = "application/pdf"
        )

        every { opprettH070.oppretterH070(any(), any()) } returns mockk(relaxed = true)

        dodsmeldingBehandler.behandle(personhendelse)

        verify(exactly = 1) { safClient.hentDokumentInnhold("123456", "dok123", "ARKIV") }
    }

    @Test
    fun `behandle henter ikke dokumentinnhold naar journalpost har ingen dokumenter`() {
        val personhendelse = mockk<Personhendelse> {
            every { personidenter } returns listOf("12345678901")
        }
        val ident = Ident.bestemIdent("12345678901")
        every { personService.hentPerson(ident) } returns mockk {
            every { utenlandskIdentifikasjonsnummer } returns listOf(
                mockk { every { utstederland } returns "SWE" }
            )
        }
        every { safClient.hentDokumentMetadata("12345678901", BrukerIdType.FNR) } returns mockk {
            every { data } returns mockk {
                every { dokumentoversiktBruker } returns mockk {
                    every { journalposter } returns listOf(
                        mockk {
                            every { journalpostId } returns "123456"
                            every { datoOpprettet } returns "2026-01-01"
                            every { tittel } returns "Test dokument"
                            every { tilleggsopplysninger } returns emptyList()
                            every { dokumenter } returns null
                        }
                    )
                }
            }
        }

        every { opprettH070.oppretterH070(any(), any()) } returns mockk(relaxed = true)

        dodsmeldingBehandler.behandle(personhendelse)

        verify(exactly = 0) { safClient.hentDokumentInnhold(any(), any(), any()) }
    }

    @Test
    fun `behandle henter ikke dokumentinnhold naar dokumenter har tom liste`() {
        val personhendelse = mockk<Personhendelse> {
            every { personidenter } returns listOf("12345678901")
        }
        val ident = Ident.bestemIdent("12345678901")
        every { personService.hentPerson(ident) } returns mockk {
            every { utenlandskIdentifikasjonsnummer } returns listOf(
                mockk { every { utstederland } returns "SWE" }
            )
        }
        every { safClient.hentDokumentMetadata("12345678901", BrukerIdType.FNR) } returns mockk {
            every { data } returns mockk {
                every { dokumentoversiktBruker } returns mockk {
                    every { journalposter } returns listOf(
                        mockk {
                            every { journalpostId } returns "123456"
                            every { datoOpprettet } returns "2026-01-01"
                            every { tittel } returns "Test dokument"
                            every { tilleggsopplysninger } returns emptyList()
                            every { dokumenter } returns emptyList()
                        }
                    )
                }
            }
        }

        every { opprettH070.oppretterH070(any(), any()) } returns mockk(relaxed = true)

        dodsmeldingBehandler.behandle(personhendelse)

        verify(exactly = 0) { safClient.hentDokumentInnhold(any(), any(), any()) }
    }

    @Test
    fun `behandle velger første gyldige ident fra listen`() {
        val personhendelse = mockk<Personhendelse> {
            every { personidenter } returns listOf("ugyldig", "12345678901", "98765432100")
        }
        val ident = Ident.bestemIdent("12345678901")
        every { personService.hentPerson(ident) } returns mockk {
            every { utenlandskIdentifikasjonsnummer } returns emptyList()
        }

        dodsmeldingBehandler.behandle(personhendelse)

        verify(exactly = 1) { personService.hentPerson(ident) }
    }
}