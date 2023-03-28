package no.nav.eessi.pensjon.pdl.adresseoppdatering

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_01
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.sed.Adresse
import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.Nav
import no.nav.eessi.pensjon.eux.model.sed.Pensjon
import no.nav.eessi.pensjon.eux.model.sed.Person
import no.nav.eessi.pensjon.eux.model.sed.PinItem
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import no.nav.eessi.pensjon.pdl.integrationtest.IntegrationBase
import no.nav.eessi.pensjon.pdl.integrationtest.KafkaTestConfig
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.Kontaktadresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.KontaktadresseType
import no.nav.eessi.pensjon.personoppslag.pdl.model.Metadata
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskAdresse
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
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit


@SpringBootTest( classes = [KafkaTestConfig::class, IntegrationBase.TestConfig::class])
@ActiveProfiles("integrationtest", "excludeKodeverk")
@DirtiesContext
@EmbeddedKafka(
    controlledShutdown = true,
    topics = ["eessi-basis-sedMottatt-v1"]
)
class SedListenerAdresseIT : IntegrationBase() {

    @MockkBean(name = "pdlRestTemplate")
    private lateinit var pdlRestTemplate: RestTemplate

    @Autowired
    lateinit var adresseListener: SedListenerAdresse

    @MockkBean
    lateinit var euxService: EuxService

    @MockkBean
    lateinit var kodeverkClient: KodeverkClient

    @MockkBean
    lateinit var personMottakKlient: PersonMottakKlient

    @Test
    fun `Gitt en sed hendelse som kommer på riktig topic og group_id så skal den konsumeres av adresseListener`() {
        val fnr = FodselsnummerGenerator.generateFnrForTest(70)
        every { euxService.hentSed(eq("74389487"), eq("743982")) } returns enSedFraEux(fnr)
        every { euxService.alleGyldigeSEDForBuc(any()) } returns emptyList() // Dette er fordi vi ikke bryr oss om IdentListener
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

        fun enPersonFraPDL(fnr: String) = no.nav.eessi.pensjon.personoppslag.pdl.model.Person(
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
            foedsel = null,
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