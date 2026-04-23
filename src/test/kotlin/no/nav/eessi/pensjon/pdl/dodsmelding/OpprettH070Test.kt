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
import no.nav.eessi.pensjon.eux.model.sed.Doedsfall
import no.nav.eessi.pensjon.eux.model.sed.H070
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endring
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endringstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Foedselsdato
import no.nav.eessi.pensjon.personoppslag.pdl.model.Folkeregistermetadata
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.Kjoenn
import no.nav.eessi.pensjon.personoppslag.pdl.model.KjoennType
import no.nav.eessi.pensjon.personoppslag.pdl.model.Metadata
import no.nav.eessi.pensjon.personoppslag.pdl.model.Navn
import no.nav.eessi.pensjon.personoppslag.pdl.model.PdlPerson
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskIdentifikasjonsnummer
import no.nav.eessi.pensjon.utils.toJson
import no.nav.person.pdl.leesah.Personhendelse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.exchange
import java.time.LocalDate
import java.time.LocalDateTime

class OpprettH070Test {

    private val opprettH070 = OpprettH070()

    @Test
    fun `Oppretter H070 og sender denne dersom vi får inn dødsmelding fra Sverige`() {
        val pdlPerson = PdlPerson(
            navn = Navn("Lever", "Ikke", "Lenger", metadata = mockMeta()),
            foedselsdato = Foedselsdato(1950,"1950-10-10", folkeregistermetadata = Folkeregistermetadata(), metadata = mockMeta()),
            kjoenn = Kjoenn(KjoennType.KVINNE, metadata = mockMeta()),
            utenlandskIdentifikasjonsnummer = listOf(
                UtenlandskIdentifikasjonsnummer(
                    identifikasjonsnummer = "SE1234567890",
                    utstederland = "SWE",
                    opphoert = false,
                    metadata = mockMeta()
                )
            ),
            identer = listOf(
                IdentInformasjon(
                    "12345678901",
                    IdentGruppe.FOLKEREGISTERIDENT
                )
            ),
            doedsfall = no.nav.eessi.pensjon.personoppslag.pdl.model.Doedsfall(LocalDate.now(), metadata = mockMeta() ),
            adressebeskyttelse = emptyList(),
            statsborgerskap = emptyList(),
            forelderBarnRelasjon = emptyList(),
            sivilstand = emptyList(),
        )

        val personhendelse = mockk<Personhendelse> {
            every { personidenter } returns listOf("12345678901")
            every { doedsfall } returns no.nav.person.pdl.leesah.doedsfall.Doedsfall(LocalDate.of(2024, 5, 1))
        }

        val response = opprettH070.oppretterH070(personhendelse, pdlPerson)

        assertEquals("2024-05-01", response.hnav?.bruker?.doedsfall?.doedsdato)

    }

    internal fun mockMeta(registrert: LocalDateTime = LocalDateTime.of(2010, 4, 2, 10, 14, 12)) : no.nav.eessi.pensjon.personoppslag.pdl.model.Metadata {
        return Metadata(
            listOf(
                Endring(
                    "DOLLY",
                    registrert,
                    "Dolly",
                    "FREG",
                    Endringstype.OPPRETT
                )
            ),
            false,
            "FREG",
            "23123123-12312312-123123"
        )
    }

}