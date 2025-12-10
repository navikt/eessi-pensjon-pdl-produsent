package no.nav.eessi.pensjon.pdl.adresseoppdatering

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.EessiPensjonApplication
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_01
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.sed.*
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.klienter.norg2.Norg2Klient
import no.nav.eessi.pensjon.klienter.saf.SafClient
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import no.nav.eessi.pensjon.pdl.integrationtest.IntegrationBase
import no.nav.eessi.pensjon.personoppslag.pdl.model.*
import no.nav.eessi.pensjon.personoppslag.pdl.model.Foedested
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.shared.person.FodselsnummerGenerator
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.RestTemplate
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit


@SpringBootTest(classes = [EessiPensjonApplication::class])
//@Import(KafkaTestConfig::class)
@ActiveProfiles("integrationtest", "excludeKodeverk")
@DirtiesContext
@EmbeddedKafka(
    controlledShutdown = true,
    topics = ["eessi-basis-sedMottatt-v1"]
)
class SedListenerAdresseIT : IntegrationBase() {

    @MockkBean(name = "pdlRestTemplate")
    private lateinit var pdlRestTemplate: RestTemplate

    @MockkBean(name = "safGraphQlOidcRestTemplate")
    private lateinit var safGraphQlOidcRestTemplate: RestTemplate

    @Autowired
    lateinit var adresseListener: SedListenerAdresse

    @MockkBean
    lateinit var gcpStorageService: GcpStorageService

    @MockkBean
    lateinit var norg2Klient: Norg2Klient

    @MockkBean
    lateinit var euxService: EuxService

    @MockkBean
    lateinit var kodeverkClient: KodeverkClient

    @MockkBean
    lateinit var personMottakKlient: PersonMottakKlient

    @MockkBean
    lateinit var safClient: SafClient

    @Test
    fun `Gitt en sed hendelse som kommer på riktig topic og group_id så skal den konsumeres av adresseListener`() {
        val fnr = FodselsnummerGenerator.generateFnrForTest(70)
        every { euxService.hentSed(eq("74389487"), eq("743982")) } returns enSedFraEux(fnr)
        every { personService.hentPerson(NorskIdent(fnr)) } returns enPersonFraPDL(fnr)
        every { kodeverkClient.finnLandkode("SE") } returns "SWE"
        every { personMottakKlient.opprettPersonopplysning(any()) } returns true

        kafkaTemplate.sendDefault(enSedHendelse().toJson())

        adresseListener.latch.await(20, TimeUnit.SECONDS)
        assertEquals(0, adresseListener.latch.count)

        verify(exactly = 1) { personMottakKlient.opprettPersonopplysning(any()) }
    }

    companion object {
        fun enSedFraEux(fnr: String) = SED(
            type = SedType.P2100,
            sedGVer = null,
            sedVer = null,
            nav = Nav(
                eessisak = null,
                bruker = Bruker(
                    mor = null,
                    far = null,
                    person = Person(
                        pin = listOf(
                            null ?: PinItem(
                                identifikator = fnr,
                                land = "NO"
                            )
                        ),
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
                    adresse = Adresse(
                        gate = "Nedoverbakken 2",
                        bygning = null,
                        by = "Tulleby",
                        postnummer = "SE-48940",
                        region = "Brannland",
                        land = "SE",
                        kontaktpersonadresse = null,
                    ),
                    arbeidsforhold = null,
                    bank = null
                )
            ),
            pensjon = Pensjon()
        )

        fun enSedHendelse(): SedHendelse {
            val sedHendelse: SedHendelse = SedHendelse(
                sektorKode = "P",
                bucType = P_BUC_01,
                sedType = SedType.P2100,
                rinaSakId = "74389487",
                rinaDokumentId = "743982",
                rinaDokumentVersjon = "1",
                avsenderNavn = "Svensk institusjon",
                avsenderLand = "SE"
            )
            return sedHendelse
        }

        fun enPersonFraPDL(fnr: String): PdlPerson {
            val personfnr = Fodselsnummer.fra(fnr)
            val fdatoaar =   LocalDate.of(1921, 7, 12)
            val doeadfall =  Doedsfall(LocalDate.of(2020, 10, 1), null, mockk())

            return PdlPerson(
                identer = listOf(
                    IdentInformasjon(
                        ident = fnr,
                        gruppe = IdentGruppe.FOLKEREGISTERIDENT
                    )
                ),
                navn = null,
                adressebeskyttelse = listOf(),
                bostedsadresse = null,
                oppholdsadresse = null,
                statsborgerskap = listOf(),
                foedested = Foedested("NOR", null, null, null, Metadata(
                    endringer = emptyList(),
                    historisk = false,
                    master = "PDL",
                    opplysningsId = "654-654-564"),
                ),
                foedselsdato = Foedselsdato(fdatoaar?.year, personfnr?.getBirthDateAsIso(), null,
                    Metadata(
                    endringer = emptyList(),
                    historisk = false,
                    master = "PDL",
                    opplysningsId = "654-654-564"),
                ),
                geografiskTilknytning = null,
                kjoenn = null,
                doedsfall = null,
                forelderBarnRelasjon = listOf(),
                sivilstand = listOf(),
                kontaktadresse = Kontaktadresse(
                    coAdressenavn = "c/o Anund",
                    folkeregistermetadata = null,
                    gyldigFraOgMed = LocalDateTime.now().minusDays(5),
                    gyldigTilOgMed = LocalDateTime.now().plusDays(5),
                    metadata = Metadata(
                        endringer = emptyList(),
                        historisk = false,
                        master = "",
                        opplysningsId = "OpplysningsId"
                    ),
                    type = KontaktadresseType.Utland,
                    utenlandskAdresse = UtenlandskAdresse(
                        adressenavnNummer = "EddyRoad",
                        bygningEtasjeLeilighet = "EddyHouse",
                        bySted = "EddyCity",
                        postkode = "111",
                        regionDistriktOmraade = "Stockholm",
                        landkode = "SWE"
                    )
                ),
                kontaktinformasjonForDoedsbo = null,
                utenlandskIdentifikasjonsnummer = listOf()
            )
        }
    }
}