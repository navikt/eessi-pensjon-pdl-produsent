package no.nav.eessi.pensjon.oppgaverouting

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.SakStatus
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.AVSLUTTET
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.LOPENDE
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.buc.SakType.*
import no.nav.eessi.pensjon.klienter.norg2.Norg2ArbeidsfordelingItem
import no.nav.eessi.pensjon.klienter.norg2.Norg2Klient
import no.nav.eessi.pensjon.klienter.norg2.Norg2Service
import no.nav.eessi.pensjon.oppgaverouting.Enhet.*
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPersonPDL
import no.nav.eessi.pensjon.personoppslag.pdl.model.Relasjon.FORSIKRET
import no.nav.eessi.pensjon.personoppslag.pdl.model.Relasjon.GJENLEVENDE
import no.nav.eessi.pensjon.personoppslag.pdl.model.SEDPersonRelasjon
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDate
import java.util.*

val norg2Klient = mockk<Norg2Klient>()
val norg2Service = Norg2Service(norg2Klient)
val routingService = OppgaveRoutingService(norg2Service)

fun irrelevantDato() = LocalDate.MIN
internal class OppgaveRoutingServiceTest {

    companion object {
        private const val DUMMY_FNR = "09035225916" // Testbruker SLAPP SKILPADDE

        const val dummyTilknytning = "032342"
        val MANGLER_LAND = null as String?
        const val NORGE: String = "NOR"
        const val UTLAND: String = "SE"

        // NFP krets er en person mellom 18 og 60 år
        val alder18aar: LocalDate = LocalDate.now().minusYears(18).minusDays(1)
        val alder59aar: LocalDate = LocalDate.now().minusYears(60).plusDays(1)

        // NAY krets er en person yngre enn 18 år eller eldre enn 60 år
        val alder17aar: LocalDate = LocalDate.now().minusYears(18).plusDays(1)
        val alder60aar: LocalDate = LocalDate.now().minusYears(60)
    }


    @Test
    fun `Gitt manglende ytelsestype for P_BUC_10 saa send oppgave til PENSJON_UTLAND`() {
        val enhet = routingService.route(
            OppgaveRoutingRequest(
                aktorId = "010101010101",
                fdato = irrelevantDato(),
                landkode = MANGLER_LAND,
                bucType = P_BUC_10,
                hendelseType = HendelseType.SENDT
            )
        )
        assertEquals(enhet, PENSJON_UTLAND)
    }

    class Routing_P_BUC_02 {
        private companion object {
            @JvmStatic
            fun arguments() =
                Arrays.stream(
                    arrayOf(
                        TestArgumentsPBuc02(NFP_UTLAND_AALESUND, NORGE, GJENLEV),
                        TestArgumentsPBuc02(UFORE_UTLANDSTILSNITT, NORGE, UFOREP, LOPENDE),
                        TestArgumentsPBuc02(UFORE_UTLANDSTILSNITT, NORGE, UFOREP, AVSLUTTET),
                        TestArgumentsPBuc02(NFP_UTLAND_AALESUND, NORGE, BARNEP),

                        TestArgumentsPBuc02(NFP_UTLAND_AALESUND, NORGE, ALDER),
                        TestArgumentsPBuc02(UFORE_UTLANDSTILSNITT, NORGE, UFOREP, LOPENDE),

                        TestArgumentsPBuc02(NFP_UTLAND_AALESUND, NORGE, ALDER),
                        TestArgumentsPBuc02(UFORE_UTLAND, UTLAND, UFOREP, LOPENDE),
                        TestArgumentsPBuc02(UFORE_UTLAND, UTLAND, UFOREP, AVSLUTTET),
                        TestArgumentsPBuc02(ID_OG_FORDELING, UTLAND),
                        TestArgumentsPBuc02(ID_OG_FORDELING, NORGE),

                        TestArgumentsPBuc02(UFORE_UTLAND, UTLAND, UFOREP, LOPENDE),
                        TestArgumentsPBuc02(PENSJON_UTLAND, UTLAND, BARNEP),
                        TestArgumentsPBuc02(PENSJON_UTLAND, UTLAND, GJENLEV),
                        TestArgumentsPBuc02(PENSJON_UTLAND, UTLAND, ALDER),
                    )
                )
        }
        data class TestArgumentsPBuc02(
            val expectedResult: Enhet,
            val landkode: String?,
            val saktype: SakType? = null,
            val sakStatus: SakStatus? = null
        )

        private fun opprettSakInfo(sakStatus: SakStatus): SakInformasjon {
            return SakInformasjon(null, UFOREP, sakStatus)
        }

        @ParameterizedTest
        @MethodSource("arguments")
        fun     `Routing for P_BUC_02'er`(arguments: TestArgumentsPBuc02) {

            assertEquals(
                arguments.expectedResult,
                routingService.route(
                    OppgaveRoutingRequest(
                        aktorId = "01010101010",
                        fdato = irrelevantDato(),
                        landkode = arguments.landkode,
                        bucType = P_BUC_02,
                        saktype = arguments.saktype,
                        sakInformasjon = arguments.sakStatus?.let { opprettSakInfo(it) },
                        hendelseType = HendelseType.SENDT
                    )
                )
            )
        }
    }

    data class TestArguments(
        val expectedResult: Enhet,
        val alder: LocalDate,
        val landkode: String?,
        val saktype: SakType?
    )

    class Routing_P_BUC_10 {
        private companion object {
            @JvmStatic
            fun arguments() =
                Arrays.stream(
                    arrayOf(
                        TestArguments(PENSJON_UTLAND, alder18aar, null, ALDER),
                        TestArguments(ID_OG_FORDELING, alder18aar, NORGE, ALDER),
                        TestArguments(PENSJON_UTLAND, alder18aar, UTLAND, ALDER),
                        TestArguments(PENSJON_UTLAND, alder17aar, null, ALDER),
                        TestArguments(ID_OG_FORDELING, alder17aar, NORGE, ALDER),
                        TestArguments(PENSJON_UTLAND, alder17aar, UTLAND, ALDER),

                        TestArguments(ID_OG_FORDELING, alder18aar, NORGE, GJENLEV),
                        TestArguments(PENSJON_UTLAND, alder18aar, UTLAND, GJENLEV),
                        TestArguments(PENSJON_UTLAND, alder17aar, null, GJENLEV),
                        TestArguments(ID_OG_FORDELING, alder17aar, NORGE, GJENLEV),
                        TestArguments(PENSJON_UTLAND, alder17aar, UTLAND, GJENLEV),

                        TestArguments(UFORE_UTLAND, alder18aar, null, UFOREP),
                        TestArguments(UFORE_UTLANDSTILSNITT, alder18aar, NORGE, UFOREP),
                        TestArguments(UFORE_UTLAND, alder18aar, UTLAND, UFOREP),
                        TestArguments(UFORE_UTLAND, alder17aar, null, UFOREP),
                        TestArguments(UFORE_UTLANDSTILSNITT, alder17aar, NORGE, UFOREP),
                        TestArguments(UFORE_UTLAND, alder17aar, UTLAND, UFOREP),

                        TestArguments(PENSJON_UTLAND, alder59aar, null, ALDER),
                        TestArguments(ID_OG_FORDELING, alder59aar, NORGE, ALDER),
                        TestArguments(PENSJON_UTLAND, alder59aar, UTLAND, ALDER),
                        TestArguments(PENSJON_UTLAND, alder60aar, null, ALDER),
                        TestArguments(ID_OG_FORDELING, alder60aar, NORGE, ALDER),
                        TestArguments(PENSJON_UTLAND, alder60aar, UTLAND, ALDER),
                    )
                )
        }

        @ParameterizedTest
        @MethodSource("arguments")
        fun `Routing_P_BUC_10`(arguments: TestArguments) {

            assertEquals(
                arguments.expectedResult,
                routingService.route(
                    OppgaveRoutingRequest(
                        aktorId = "01010101010",
                        fdato = arguments.alder,
                        bucType = P_BUC_10,
                        landkode = arguments.landkode,
                        saktype = arguments.saktype,
                        hendelseType = HendelseType.SENDT
                    )
                )
            )
        }
    }

    @Test
    fun `Routing for P_BUC_10 mottatt med bruk av Norg2 tjeneste`() {
        val enhetlist = fromResource("/norg2/norg2arbeidsfordelig4862med-viken-result.json")

        every { norg2Klient.hentArbeidsfordelingEnheter(any()) } returns enhetlist

        val personRelasjon =
            SEDPersonRelasjon(Fodselsnummer.fra(DUMMY_FNR), FORSIKRET, ALDER, SedType.P15000, rinaDocumentId =  "3123123")
        val identifisertPerson =
            IdentifisertPersonPDL("01010101010",  "NOR", "3005", personRelasjon, personListe = emptyList())

        val sedHendelseModel = SedHendelse(
            1232312L, "2321313", "P", P_BUC_10, "32131", avsenderId = "12313123",
            "SE", "SE", "2312312", "NO", "NO", "23123123", "1",
            SedType.P15000, null
        )

        val oppgaveroutingrequest = OppgaveRoutingRequest.fra(
            identifisertPerson,
            alder60aar,
            ALDER,
            sedHendelseModel,
            HendelseType.MOTTATT,
            SakInformasjon(
                "32131",
                ALDER,
                LOPENDE
            ),
        )

        val result = routingService.route(oppgaveroutingrequest)
        assertEquals(FAMILIE_OG_PENSJONSYTELSER_OSLO, result)
    }

    @Test
    fun `Gitt gjenlevendesak for P_BUC_02 mottatt når bruk av Norg2 tjeneste benyttes så routes det til NFP_UTLAND_AALESUND`() {
        val json = """
            {
            "id": 100026861,
            "diskresjonskode": "ANY",
            "oppgavetype": "ANY",
            "behandlingstype": "ae0104",
            "behandlingstema": "ab0011",
            "tema": "PEN",
            "temagruppe": "ANY",
            "geografiskOmraade": "3005",
            "enhetId": 100000617,
            "enhetNr": "0001",
            "enhetNavn": "NAV Pensjon Utland",
            "skalTilLokalkontor": false,
            "gyldigFra": "2017-09-30"
            }
        """.trimIndent()
        val mappedResponse = mapJsonToAny<Norg2ArbeidsfordelingItem>(json)


        every { norg2Klient.hentArbeidsfordelingEnheter(any()) } returns listOf(mappedResponse)

        val personRelasjon =
            SEDPersonRelasjon(Fodselsnummer.fra(DUMMY_FNR), GJENLEVENDE, GJENLEV, SedType.P2100, rinaDocumentId =  "3123123")
        val identifisertPerson =
            IdentifisertPersonPDL("01010101010",  "NOR", "3005", personRelasjon, personListe = emptyList())

        val sedHendelseModel = SedHendelse(
            1232312L, "2321313", "P", P_BUC_02, "32131", avsenderId = "12313123",
            "SE", "SE", "2312312", "NO", "NO", "23123123", "1",
            SedType.P2100, null
        )

        val oppgaveroutingrequest = OppgaveRoutingRequest.fra(
            identifisertPerson,
            alder60aar,
            GJENLEV,
            sedHendelseModel,
            HendelseType.MOTTATT,
            null,
        )

        val result = routingService.route(oppgaveroutingrequest)
        assertEquals(NFP_UTLAND_AALESUND, result)

    }

    @Test
    fun `Gitt barnePensjon for P_BUC_02 mottatt når bruk av Norg2 tjeneste benyttes så routes det til PensjonUtland`() {
        val json = """
            {
            "id": 100026861,
            "diskresjonskode": "ANY",
            "oppgavetype": "ANY",
            "behandlingstype": "ae0107",
            "behandlingstema": "ab0255",
            "tema": "PEN",
            "temagruppe": "ANY",
            "geografiskOmraade": "3005",
            "enhetId": 100000617,
            "enhetNr": "0001",
            "enhetNavn": "NAV Pensjon Utland",
            "skalTilLokalkontor": false,
            "gyldigFra": "2017-09-30"
            }
        """.trimIndent()
        val mappedResponse = mapJsonToAny<Norg2ArbeidsfordelingItem>(json)


        every { norg2Klient.hentArbeidsfordelingEnheter(any()) } returns listOf(mappedResponse)

        val personRelasjon =
            SEDPersonRelasjon(Fodselsnummer.fra(DUMMY_FNR), GJENLEVENDE, BARNEP, SedType.P2100, rinaDocumentId =  "3123123")
        val identifisertPerson = IdentifisertPersonPDL(
            "01010101010",
            "SWE",
            "3005",
            personRelasjon,
            personListe = emptyList()
        )

        val sedHendelseModel = SedHendelse(
            1232312L, "2321313", "P", P_BUC_02, "32131", avsenderId = "12313123",
            "SE", "SE", "2312312", "NO", "NO", "23123123", "1",
            SedType.P2100, null
        )

        val oppgaveroutingrequest = OppgaveRoutingRequest.fra(
            identifisertPerson,
            alder60aar,
            BARNEP,
            sedHendelseModel,
            HendelseType.MOTTATT,
            null,
        )

        val result = routingService.route(oppgaveroutingrequest)
        assertEquals(PENSJON_UTLAND, result)

    }

    @Test
    fun `Gitt uføresak for P_BUC_02 mottatt når bruk av Norg2 tjeneste benyttes så routes det til UFORE_UTLAND`() {
        val json = """
            {
            "id": 100026861,
            "diskresjonskode": "ANY",
            "oppgavetype": "ANY",
            "behandlingstype": "ae0107",
            "behandlingstema": "ANY",
            "tema": "UFO",
            "temagruppe": "ANY",
            "geografiskOmraade": "ANY",
            "enhetId": 100000617,
            "enhetNr": "4475",
            "enhetNavn": "UFORE UTLAND",
            "skalTilLokalkontor": false,
            "gyldigFra": "2017-09-30"
            }
        """.trimIndent()
        val mappedResponse = mapJsonToAny<Norg2ArbeidsfordelingItem>(json)

        every { norg2Klient.hentArbeidsfordelingEnheter(any()) } returns listOf(mappedResponse)

        val personRelasjon =
            SEDPersonRelasjon(Fodselsnummer.fra(DUMMY_FNR), FORSIKRET, UFOREP, SedType.P2200, rinaDocumentId =  "3123123")
        val identifisertPerson =
            IdentifisertPersonPDL("01010101010",  "SWE", null, personRelasjon, personListe = emptyList())

        val sedHendelseModel = SedHendelse(
            1232312L, "2321313", "P", P_BUC_03, "32131", avsenderId = "12313123",
            "NO", "NO", "2312312", "SE", "SE", "23123123", "1",
            SedType.P2200, null
        )

        val oppgaveroutingrequest = OppgaveRoutingRequest.fra(
            identifisertPerson,
            alder60aar,
            UFOREP,
            sedHendelseModel,
            HendelseType.SENDT,
            null,
        )

        val result = routingService.route(oppgaveroutingrequest)
        assertEquals(UFORE_UTLAND, result)
    }

    class Routing_vanligeBucs {
        data class TestArgumentsBucs(
            val expectedResult: Enhet,
            val bucType: BucType,
            val landkode: String? = null,
            val geografiskTilknytning: String? = null,
            val fdato: LocalDate? = null,
            val saksType: SakType? = null,
            val adressebeskyttet: Boolean? = false
        )

        private companion object {
            @JvmStatic
            fun arguments() =
                Arrays.stream(
                    arrayOf(
                        TestArgumentsBucs(UFORE_UTLAND, P_BUC_03,   fdato = irrelevantDato()),
                        TestArgumentsBucs(UFORE_UTLANDSTILSNITT, P_BUC_03, NORGE,  fdato = irrelevantDato()),
                        TestArgumentsBucs(UFORE_UTLAND, P_BUC_03, UTLAND,  fdato = irrelevantDato()),

                        TestArgumentsBucs(PENSJON_UTLAND, P_BUC_10, UTLAND, fdato = alder60aar, saksType = GJENLEV),
                        TestArgumentsBucs(DISKRESJONSKODE, P_BUC_10, UTLAND, fdato = alder60aar, adressebeskyttet = true, saksType = GJENLEV),
                    )
                )
        }

        @ParameterizedTest
        @MethodSource("arguments")
        fun `Ruting for vanlige BUCer`(arguments: TestArgumentsBucs) {
            assertEquals(
                arguments.expectedResult,
                routingService.route(
                    OppgaveRoutingRequest(
                        aktorId = "01010101010",
                        fdato = arguments.fdato ?: irrelevantDato(),
                        geografiskTilknytning = arguments.geografiskTilknytning,
                        bucType = arguments.bucType,
                        landkode = arguments.landkode,
                        hendelseType = HendelseType.SENDT,
                        harAdressebeskyttelse = arguments.adressebeskyttet ?: false,
                        saktype = arguments.saksType
                    )
                )
            )
        }
    }

    @Test
    fun testEnumEnhets() {
        assertEquals(PENSJON_UTLAND, Enhet.getEnhet("0001"))
        assertEquals(FAMILIE_OG_PENSJONSYTELSER_OSLO, Enhet.getEnhet("4803"))
        assertEquals(DISKRESJONSKODE, Enhet.getEnhet("2103"))
    }

    private fun fromResource(file: String): List<Norg2ArbeidsfordelingItem> {
        val json = javaClass.getResource(file)!!.readText()

        return mapJsonToAny(json)
    }

}
