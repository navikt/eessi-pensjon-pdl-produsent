package no.nav.eessi.pensjon.pdl.identoppdatering

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.models.PdlEndringOpplysning
import no.nav.eessi.pensjon.oppgave.OppgaveData
import no.nav.eessi.pensjon.oppgave.OppgaveHandler
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.retry.annotation.EnableRetry
import org.springframework.stereotype.Component
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.web.client.HttpClientErrorException

@ActiveProfiles("retryConfigOverride")
@SpringJUnitConfig(classes = [
    SedHendelseIdentBehandler::class,
    SedHendelseIdentBehandlerRetryLogger::class,
    TestSedHendelseIdentBehandlerRetryConfig::class]
)
@EnableRetry
private class SedHendelseIdentBehandlerTest {

    @MockkBean
    lateinit var vurderIdentoppdatering: VurderIdentoppdatering

    @MockkBean
    lateinit var personMottakKlient: PersonMottakKlient

    @MockkBean
    lateinit var oppgaveHandler: OppgaveHandler

    @Autowired
    lateinit var sedHendelseIdentBehandler: SedHendelseIdentBehandler

    @Test
    fun `Gitt en Oppdatering så kaller vi opprettPersonopplysning`() {

        every { vurderIdentoppdatering.vurderUtenlandskIdent(any()) } returns
                VurderIdentoppdatering.Oppdatering("En oppdatering", PdlEndringOpplysning(listOf()))
        every { personMottakKlient.opprettPersonopplysning(any()) } returns true

        sedHendelseIdentBehandler.behandle(enSedHendelseAsJson())

        verify(exactly = 1) { vurderIdentoppdatering.vurderUtenlandskIdent(any()) }
        verify(exactly = 1) { personMottakKlient.opprettPersonopplysning(any()) }
        verify(exactly = 0) { oppgaveHandler.opprettOppgaveForUid(any()) }
    }

    @Test
    fun `Gitt Oppgave så kaller vi opprettOppgave, men IKKE opprettPersonopplysning`() {

        val enSedHendelse = enSedHendelse()

        every { vurderIdentoppdatering.vurderUtenlandskIdent(any()) } returns
                VurderIdentoppdatering.Oppgave("Oppgave", OppgaveData(enSedHendelse, enIdentifisertPerson()))
        every { oppgaveHandler.opprettOppgaveForUid(any()) } returns true

        sedHendelseIdentBehandler.behandle(enSedHendelse.toJson())

        verify(exactly = 1) { vurderIdentoppdatering.vurderUtenlandskIdent(any()) }
        verify(exactly = 0) { personMottakKlient.opprettPersonopplysning(any()) }
        verify(exactly = 1) { oppgaveHandler.opprettOppgaveForUid(any()) }
    }

    @Test
    fun `Gitt en at vi får 423 LOCKED fra PDL så gjør vi retry på hele prosessen`() {

        every { vurderIdentoppdatering.vurderUtenlandskIdent(any()) } throws HttpClientErrorException(HttpStatus.LOCKED)

        val ex = org.junit.jupiter.api.assertThrows<HttpClientErrorException> {
            sedHendelseIdentBehandler.behandle(enSedHendelseAsJson())
        }

        assertEquals(HttpStatus.LOCKED, ex.statusCode)

        verify(exactly = 3) { vurderIdentoppdatering.vurderUtenlandskIdent(any()) }
        verify(exactly = 0) { personMottakKlient.opprettPersonopplysning(any()) }
        verify(exactly = 0) { oppgaveHandler.opprettOppgaveForUid(any()) }
    }

    private fun enSedHendelseAsJson() = enSedHendelse().toJson()

    private fun enSedHendelse() = SedHendelse(
            sektorKode = "P",
            bucType = P_BUC_01,
            sedType = SedType.P2100,
            rinaSakId = "74389487",
            rinaDokumentId = "743982",
            rinaDokumentVersjon = "1",
            avsenderNavn = "Svensk institusjon",
            avsenderLand = "SE"
        )

    private fun enIdentifisertPerson() =
        IdentifisertPerson(uidFraPdl = emptyList(), aktoerId = "000", erDoed = false, harAdressebeskyttelse = false, landkode = null, fnr = null, personListe = null, personRelasjon = null, geografiskTilknytning = null, kontaktAdresse = null)

}

@Profile("retryConfigOverride")
@Component("sedHendelseIdentBehandlerRetryConfig")
data class TestSedHendelseIdentBehandlerRetryConfig(val initialRetryMillis: Long = 10L)