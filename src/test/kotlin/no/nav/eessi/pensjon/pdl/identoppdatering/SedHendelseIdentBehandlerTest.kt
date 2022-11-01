package no.nav.eessi.pensjon.pdl.identoppdatering

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.UtenlandskId
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.eux.model.sed.Adresse
import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.Nav
import no.nav.eessi.pensjon.eux.model.sed.Pensjon
import no.nav.eessi.pensjon.eux.model.sed.PinItem
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.models.SedHendelse
import no.nav.eessi.pensjon.oppgave.OppgaveHandler
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import no.nav.eessi.pensjon.pdl.adresseoppdatering.SedListenerAdresseIT
import no.nav.eessi.pensjon.pdl.validering.LandspesifikkValidering
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import no.nav.eessi.pensjon.personidentifisering.SEDPersonRelasjon
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.AdressebeskyttelseGradering
import no.nav.eessi.pensjon.personoppslag.pdl.model.Doedsfall
import no.nav.eessi.pensjon.personoppslag.pdl.model.Folkeregistermetadata
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.Kontaktadresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.KontaktadresseType
import no.nav.eessi.pensjon.personoppslag.pdl.model.Metadata
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.Person
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskAdresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskIdentifikasjonsnummer
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
import java.time.LocalDate
import java.time.LocalDateTime

private const val FNR = "11067122781"
private const val SVENSK_FNR = "512020-1234"
private const val FINSK_FNR = "130177-308T"
private const val SOME_FNR = "1234567799"
private const val SVERIGE = "SE"
private const val NORGE = "NO"

@ActiveProfiles("retryConfigOverride")
@SpringJUnitConfig(classes = [
    SedHendelseIdentBehandler::class,
    IdentOppdatering::class,
    BehandleIdentRetryLogger::class,
    LandspesifikkValidering::class,
    TestSedHendelseIdentBehandlerRetryConfig::class]
)
@EnableRetry
private class SedHendelseIdentBehandlerTest {

    @MockkBean
    lateinit var euxService: EuxService

    @MockkBean
    lateinit var personService: PersonService

    @MockkBean
    lateinit var kodeverkClient: KodeverkClient

    @MockkBean
    lateinit var personMottakKlient: PersonMottakKlient

    @MockkBean
    lateinit var oppgaveHandler: OppgaveHandler

    @Autowired
    lateinit var sedHendelseIdentBehandler: SedHendelseIdentBehandler

    @Test
    fun `Gitt at vi har en SED med norsk fnr som skal oppdateres til pdl, der PDL har en aktoerid inne i PDL saa skal Oppdateringsmeldingen til PDL ha norsk FNR og ikke aktoerid`() {
        every { kodeverkClient.finnLandkode("SE") } returns "SWE"
        every { kodeverkClient.finnLandkode("FI") } returns "FIN"
        every { kodeverkClient.finnLandkode("FIN") } returns "FI"

        val identifisertPerson= identifisertPerson(uidFraPdl = listOf(utenlandskIdentifikasjonsnummer(SVENSK_FNR)))

        every { personService.hentPerson(NorskIdent(FNR)) } returns
                personFraPDL(id = FNR).copy(identer = listOf(IdentInformasjon("1234567891234", IdentGruppe.AKTORID), IdentInformasjon(FNR, IdentGruppe.FOLKEREGISTERIDENT)))
                        .copy(utenlandskIdentifikasjonsnummer = listOf(utenlandskIdentifikasjonsnummer(FINSK_FNR).copy(utstederland = "FIN")))

        every { euxService.hentSed(any(), any()) } returns
                sed(id = FNR, land = NORGE, pinItem =  listOf(
                        PinItem(identifikator = "5 12 020-1234", land = SVERIGE),
                        PinItem(identifikator = FNR, land = NORGE))
                )

        every { oppgaveHandler.opprettOppgaveForUid(any(), UtenlandskId( SVENSK_FNR, SVERIGE), identifisertPerson) } returns false
        every { oppgaveHandler.opprettOppgaveForUid(any(), any(), any()) } returns true

        every { personMottakKlient.opprettPersonopplysning(any()) } throws HttpClientErrorException(HttpStatus.LOCKED)

        val ex = org.junit.jupiter.api.assertThrows<HttpClientErrorException> {
            sedHendelseIdentBehandler.behandle(SedListenerAdresseIT.enSedHendelse().toJson())
        }

        assertEquals(HttpStatus.LOCKED, ex.statusCode)

        verify(exactly = 3) { personMottakKlient.opprettPersonopplysning(any()) }

    }

    private fun identifisertPerson(
        erDoed: Boolean = false, uidFraPdl: List<UtenlandskIdentifikasjonsnummer> = emptyList()
    ) = IdentifisertPerson(
        fnr = Fodselsnummer.fra(FNR),
        uidFraPdl = uidFraPdl,
        aktoerId = "123456789351",
        landkode = null,
        geografiskTilknytning = null,
        harAdressebeskyttelse = erDoed,
        personListe = null,
        personRelasjon = SEDPersonRelasjon(
            relasjon = Relasjon.ANNET,
            fnr = Fodselsnummer.fra(FNR),
            rinaDocumentId = "12345"
        ),
        erDoed = erDoed,
        kontaktAdresse = null,
    )

    private fun utenlandskIdentifikasjonsnummer(fnr: String) = UtenlandskIdentifikasjonsnummer(
        identifikasjonsnummer = fnr,
        utstederland = "POL",
        opphoert = false,
        folkeregistermetadata = Folkeregistermetadata(
            gyldighetstidspunkt = LocalDateTime.now(),
            ajourholdstidspunkt = LocalDateTime.now(),
        ),
        metadata = metadata()
    )

    private fun metadata() = Metadata(
        endringer = emptyList(),
        historisk = false,
        master = "PDL",
        opplysningsId = "opplysningsId"
    )

    private fun sedHendelse(
        bucType: BucType = BucType.P_BUC_01,
        sedType: SedType = SedType.P2000,
        avsenderLand: String = "SE",
        avsenderNavn: String? = "Utenlandsk institusjon"
    ) = SedHendelse(
        sektorKode = "P",
        avsenderLand = avsenderLand,
        bucType = bucType,
        rinaSakId = "123456479867",
        rinaDokumentId = "SOME_DOKUMENT_ID",
        rinaDokumentVersjon = "SOME_RINADOKUMENT_VERSION",
        sedType = sedType,
        avsenderNavn = avsenderNavn
    )

    private fun sed(id: String = SOME_FNR,
                     brukersAdresse: Adresse? = null,
                     land: String?,
                     pinItem: List<PinItem>? = listOf(PinItem(land = land, identifikator = id)) ) = SED(
        type = SedType.P2000,
        sedGVer = null,
        sedVer = null,
        nav = Nav(
            eessisak = null,
            bruker = Bruker(
                mor = null,
                far = null,
                person = no.nav.eessi.pensjon.eux.model.sed.Person(
                    pin = pinItem,
                    pinland = null,
                    statsborgerskap = null,
                    etternavn = null,
                    etternavnvedfoedsel = null,
                    fornavn = null,
                    fornavnvedfoedsel = null,
                    tidligerefornavn = null,
                    tidligereetternavn = null,
                    kjoenn = null,
                    foedested = null,
                    foedselsdato = null,
                    sivilstand = null,
                    relasjontilavdod = null,
                    rolle = null
                ),
                adresse = brukersAdresse,
                arbeidsforhold = null,
                bank = null
            )
        ),
        pensjon = Pensjon()
    )

    private fun personFraPDL(
        id: String = SOME_FNR,
        adressebeskyttelse: List<AdressebeskyttelseGradering> = listOf(),
        utenlandskAdresse: UtenlandskAdresse? = null,
        opplysningsId: String = "DummyOpplysningsId",
        gyldigFraOgMed: LocalDateTime = LocalDateTime.now().minusDays(10),
        gyldigTilOgMed: LocalDateTime = LocalDateTime.now().plusDays(10),
        doedsdato: LocalDate? = null
    ): Person = Person(
        identer = listOf(IdentInformasjon(id, IdentGruppe.FOLKEREGISTERIDENT)),
        navn = null,
        adressebeskyttelse = adressebeskyttelse,
        bostedsadresse = null,
        oppholdsadresse = null,
        statsborgerskap = listOf(),
        foedsel = null,
        geografiskTilknytning = null,
        kjoenn = null,
        doedsfall = Doedsfall(
            doedsdato = doedsdato,
            folkeregistermetadata = null,
            metadata= metadata()
        ),
        forelderBarnRelasjon = listOf(),
        sivilstand = listOf(),
        kontaktadresse = Kontaktadresse(
            coAdressenavn = "c/o Anund",
            folkeregistermetadata = null,
            gyldigFraOgMed = gyldigFraOgMed,
            gyldigTilOgMed = gyldigTilOgMed,
            metadata = Metadata(
                endringer = emptyList(),
                historisk = false,
                master = "",
                opplysningsId = opplysningsId
            ),
            type = KontaktadresseType.Utland,
            utenlandskAdresse = utenlandskAdresse
        ),
        kontaktinformasjonForDoedsbo = null,
        utenlandskIdentifikasjonsnummer = listOf()
    )
}

@Profile("retryConfigOverride")
@Component("identBehandlerRetryConfig")
data class TestSedHendelseIdentBehandlerRetryConfig(val initialRetryMillis: Long = 10L)