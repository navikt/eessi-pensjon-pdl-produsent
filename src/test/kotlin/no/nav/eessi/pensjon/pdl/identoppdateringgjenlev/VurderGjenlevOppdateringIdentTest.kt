package no.nav.eessi.pensjon.pdl.identoppdateringgjenlev

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.sed.Adresse
import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.EessisakItem
import no.nav.eessi.pensjon.eux.model.sed.Nav
import no.nav.eessi.pensjon.eux.model.sed.P2100
import no.nav.eessi.pensjon.eux.model.sed.P5000
import no.nav.eessi.pensjon.eux.model.sed.P5000Pensjon
import no.nav.eessi.pensjon.eux.model.sed.Person
import no.nav.eessi.pensjon.eux.model.sed.PinItem
import no.nav.eessi.pensjon.klienter.saf.SafClient
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.oppgave.OppgaveOppslag
import no.nav.eessi.pensjon.pdl.AKTOERID
import no.nav.eessi.pensjon.pdl.FNR
import no.nav.eessi.pensjon.pdl.FNR_MED_MELLOMROM
import no.nav.eessi.pensjon.pdl.IdentBaseTest
import no.nav.eessi.pensjon.pdl.OppgaveModel.IngenOppdatering
import no.nav.eessi.pensjon.pdl.OppgaveModel.Oppdatering
import no.nav.eessi.pensjon.pdl.OppgaveModel.OppgaveGjenlev
import no.nav.eessi.pensjon.pdl.SOME_FNR
import no.nav.eessi.pensjon.pdl.validering.LandspesifikkValidering
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.skyscreamer.jsonassert.JSONAssert

class VurderGjenlevOppdateringIdentTest : IdentBaseTest() {

    var euxService: EuxService = mockk(relaxed = true)
    var kodeverkClient: KodeverkClient = mockk(relaxed = true)
    var safClient: SafClient = mockk(relaxed = true)
    var oppgaveOppslag: OppgaveOppslag = mockk()
    var personService: PersonService = mockk()
    var landspesifikkValidering = LandspesifikkValidering(kodeverkClient)
    lateinit var identoppdatering: VurderGjenlevOppdateringIdent

    @BeforeEach
    fun setup() {
        every { kodeverkClient.finnLandkode("PL") } returns "POL"
        every { kodeverkClient.finnLandkode("POL") } returns "PL"

        every { kodeverkClient.finnLandkode("SE") } returns "SWE"
        every { kodeverkClient.finnLandkode("SWE") } returns "SE"

        every { kodeverkClient.finnLandkode("DK") } returns "DNK"
        every { kodeverkClient.finnLandkode("DNK") } returns "DK"

        every { kodeverkClient.finnLandkode("FI") } returns "FIN"
        every { kodeverkClient.finnLandkode("FIN") } returns "FI"

        identoppdatering = VurderGjenlevOppdateringIdent(
            euxService,
            oppgaveOppslag,
            kodeverkClient,
            personService,
            landspesifikkValidering,
            mockk(),
        )
    }

    @Test
    fun `Gitt at vi har en endringsmelding med en svensk uid, med riktig format saa skal det opprettes en endringsmelding`() {
        every { personService.hentPerson(NorskIdent(FNR)) } returns
                personFraPDL(id = FNR).copy(identer = listOf(IdentInformasjon(FNR, IdentGruppe.FOLKEREGISTERIDENT)))

        every { personService.hentPerson(NorskIdent(SOME_FNR)) } returns
                personFraPDL(id = SOME_FNR).copy(
                    identer = listOf(
                        IdentInformasjon(
                            SOME_FNR,
                            IdentGruppe.FOLKEREGISTERIDENT
                        )
                    ),
                )

        val sed = sedGjenlevende(
            pinItem = listOf(
                PinItem(identifikator = "5 12 020-1234", land = "SE"),
                PinItem(identifikator = FNR, land = "NO")
            ),
            fodselsdato = "1971-06-11",
        )
        val p2100 = P2100(SedType.P2100, nav = sed.nav, pensjon = sed.pensjon)
        every { euxService.hentSed(any(), any()) } returns p2100

        assertEquals(
            Oppdatering(
                "Innsending av endringsmelding",
                pdlEndringsMelding(FNR, utstederland = "SWE")
            ),
            (identoppdatering.vurderUtenlandskGjenlevIdent(sedHendelse(avsenderLand = "SE")))
        )
    }


    @Test
    fun `Gitt at vi har en endringsmelding med en svensk uid, med riktig format men norsk fnr er ikke på stadard format saa skal det opprettes en endringsmelding med formatert norsk fnr`() {
        every { personService.hentPerson(NorskIdent(FNR)) } returns
                personFraPDL(id = FNR).copy(identer = listOf(IdentInformasjon(FNR, IdentGruppe.FOLKEREGISTERIDENT)))

        every { personService.hentPerson(NorskIdent(SOME_FNR)) } returns
                personFraPDL(id = SOME_FNR).copy(
                    identer = listOf(
                        IdentInformasjon(
                            SOME_FNR,
                            IdentGruppe.FOLKEREGISTERIDENT
                        )
                    ),
                )

        val sed = sedGjenlevende(
            pinItem = listOf(
                PinItem(identifikator = "5 12 020-1234", land = "SE"),
                PinItem(identifikator = FNR_MED_MELLOMROM, land = "NO")
            ),
            fodselsdato = "1971-06-11",
        )
        val p2100 = P2100(SedType.P2100, nav = sed.nav, pensjon = sed.pensjon)
        every { euxService.hentSed(any(), any()) } returns p2100

        assertEquals(
            Oppdatering(
                "Innsending av endringsmelding",
                pdlEndringsMelding(FNR, utstederland = "SWE")
            ),
            (identoppdatering.vurderUtenlandskGjenlevIdent(sedHendelse(avsenderLand = "SE")))
        )
    }

    @Test
    fun `Gitt at vi har en endringsmelding med en svensk uid med som mangler fdato så skal vi ikke sende en oppdatering`() {
        every { personService.hentPerson(NorskIdent(FNR)) } returns
                personFraPDL(id = FNR).copy(identer = listOf(IdentInformasjon(FNR, IdentGruppe.FOLKEREGISTERIDENT)))

        every { personService.hentPerson(NorskIdent(SOME_FNR)) } returns
                personFraPDL(id = SOME_FNR).copy(
                    identer = listOf(
                        IdentInformasjon(
                            SOME_FNR,
                            IdentGruppe.FOLKEREGISTERIDENT
                        )
                    ),
                )

        val sed = sedGjenlevende(
            pinItem = listOf(
                PinItem(identifikator = "5 12 020-1234", land = "SE"),
                PinItem(identifikator = FNR, land = "NO")
            ),
            fodselsdato = null,
        )
        val p2100 = P2100(SedType.P2100, nav = sed.nav, pensjon = sed.pensjon)
        every { euxService.hentSed(any(), any()) } returns p2100

        assertEquals(
            Oppdatering(
                "Innsending av endringsmelding",
                pdlEndringsMelding(FNR, utstederland = "SWE")
            ),
            (identoppdatering.vurderUtenlandskGjenlevIdent(sedHendelse(avsenderLand = "SE")))
        )
    }

    @Test
    fun `Gitt at vi har en P10000 der vi ikke har gjenlevende så skal vi ikke sende oppdatering på gjenlevende`() {
        every { personService.hentPerson(NorskIdent(FNR)) } returns
                personFraPDL(id = FNR).copy(identer = listOf(IdentInformasjon(FNR, IdentGruppe.FOLKEREGISTERIDENT)))

        every { personService.hentPerson(NorskIdent(SOME_FNR)) } returns
                personFraPDL(id = SOME_FNR).copy(
                    identer = listOf(
                        IdentInformasjon(
                            SOME_FNR,
                            IdentGruppe.FOLKEREGISTERIDENT
                        )
                    )
                )

        val p2100 = P2100(SedType.P2100, null, null)
        every { euxService.hentSed(any(), any()) } returns p2100

        assertEquals(
            IngenOppdatering("Seden har ingen gjenlevende"),
            (identoppdatering.vurderUtenlandskGjenlevIdent(sedHendelse(avsenderLand = "SE")))
        )
    }

    @ParameterizedTest
    @CsvSource(
        "P4000",
        "P5000",
        "P6000",
        "P7000",
        "P8000",
        "P9000",
        "P10000",
        "P15000"
    )
    fun `Gitt en P5000 med en gjenlevende som har uid som skal oppdateres`(sedType: String) {
        every { personService.hentPerson(NorskIdent(FNR)) } returns
                personFraPDL(id = FNR).copy(identer = listOf(IdentInformasjon(FNR, IdentGruppe.FOLKEREGISTERIDENT)))

        val gjenlevUid = "120281-6547"
        val sed = sedMedGjenlevende(
            gjenlevFNR = FNR,
            forsikretFnr = SOME_FNR,
            gjenlevUid = gjenlevUid,
            sedType = sedType
        )
        every { euxService.hentSed(any(), any()) } returns convertFromSedTypeToSED(sed, SedType.from(sedType)!!)

        assertEquals(
            Oppdatering(
                "Innsending av endringsmelding",
                pdlEndringsMelding(FNR, gjenlevUid, utstederland = "DNK")
            ),
            (identoppdatering.vurderUtenlandskGjenlevIdent(sedHendelse(avsenderLand = "DK")))
        )
    }

    @Test
    fun `Gitt en P2100 med en gjenlevende som har avdode sitt fnr som sitt fnr saa skal fdato på gjenlevende sjekkes og hvis ikke lik saa skal oppdateringen stoppe opp`() {
        val p2100 = javaClass.getResource("/P2100.json")!!.readText()

        every { euxService.hentSed(any(), any()) } returns mapJsonToAny<P2100>(p2100)

        JSONAssert.assertEquals(
            IngenOppdatering("Gjenlevende fdato stemmer ikke overens med fnr",
                "Gjenlevende fdato stemmer ikke overens med fnr").toJson(),
            identoppdatering.vurderUtenlandskGjenlevIdent(sedHendelse(avsenderLand = "SE")).toJson(), false
        )

    }

    @Test
    fun `En SED med to ulike PIN fra samme land skal opprette en OppgaveGjenlev`() {

        every { oppgaveOppslag.finnesOppgavenAllerede(any()) } returns false

        every { personService.hentPerson(NorskIdent(FNR)) } returns
                personFraPDL(id = FNR).copy(
                    identer = listOf(IdentInformasjon(FNR, IdentGruppe.FOLKEREGISTERIDENT), IdentInformasjon(AKTOERID, IdentGruppe.AKTORID)),
                    utenlandskIdentifikasjonsnummer = listOf((utenlandskIdentifikasjonsnummer(fnr = FNR).copy(utstederland = "DNK")))
                )

        val gjenlevUid = "120281-6547"
        val sed = sedMedGjenlevende(
            gjenlevFNR = FNR,
            gjenlevUid = gjenlevUid,
            forsikretFnr = SOME_FNR,
            sedType = SedType.P4000.name
        )
        every { euxService.hentSed(any(), any()) } returns convertFromSedTypeToSED(sed, SedType.P4000)

        val result = identoppdatering.vurderUtenlandskGjenlevIdent(sedHendelse(avsenderLand = "DK"))
        result is OppgaveGjenlev
        assertEquals(result.description, "Det finnes allerede en annen uid fra samme land (oppgave opprettes)")
        assertEquals((result as OppgaveGjenlev).oppgaveData.identifisertPerson.fnr?.value, "11067122781")
    }

    @Test
    fun `En SED med gjenlevende som mangler norsk fnr skal ikke sende oppdatering`() {

        every { euxService.hentSed(any(), any()) } returns p5000gjenlevUtenNorskPin()

        val result = identoppdatering.vurderUtenlandskGjenlevIdent(sedHendelse(avsenderLand = "DK"))
        result is OppgaveGjenlev
        assertEquals("Seden har ingen norsk pin på gjenlevende", result.description)
    }

    @Test
    fun `En SED som mangler gjenlevende skal ikke sende oppdatering`() {

        every { euxService.hentSed(any(), any()) } returns p5000gjenlevUtenNorskPin().copy(
            p5000Pensjon = null
        )

        val result = identoppdatering.vurderUtenlandskGjenlevIdent(sedHendelse(avsenderLand = "DK"))
        result is OppgaveGjenlev
        assertEquals("Seden har ingen gjenlevende", result.description)
    }

    fun p5000gjenlevUtenNorskPin(): P5000 {
        return P5000(
            type = SedType.P5000,
            nav = Nav(
                eessisak = listOf(EessisakItem(
                    institusjonsid = "UK:UK030",
                    institusjonsnavn = "Departmentfor Work and Pensions",
                    saksnummer = "432432432",
                    land = "GB"
                )),
                bruker = Bruker(
                    person = Person(
                        pin = listOf(PinItem(
                            identifikator = "SJ38985B",
                            land = "GB",
                        )),
                        foedselsdato = "1981-19-04",
                    ),
                    adresse = Adresse(
                        by = "Pavlova",
                        land = "PL",

                    ),
                ),
            ),
            p5000Pensjon = P5000Pensjon(
                gjenlevende = Bruker(
                    person = Person(
                        etternavn = "Pavlova",
                        fornavn = "Pavel",
                        foedselsdato = "2006-05-01"
                    ),
                )
            )
        )
    }

    private fun sedMedGjenlevende(gjenlevFNR: String, forsikretFnr: String, gjenlevUid: String, sedType: String): String {
        val sed = """
                {
                   "pensjon":{
                      "medlemskapboarbeid":{
                         "enkeltkrav":{
                            "krav":"20"
                         },
                         "gyldigperiode":"1"
                      },
                      "medlemskapTotal":[
                         {
                            "type":"10",
                            "sum":{
                               "aar":"4",
                               "maaneder":"11",
                               "dager":{
                                  "type":"7",
                                  "nr":"2"
                               }
                            },
                            "relevans":"111"
                         }
                      ],
                      "gjenlevende":{
                         "person":{
                            "pin":[
                               {
                                  "identifikator":"$gjenlevUid",
                                  "land":"DK"
                               },
                               {
                                  "identifikator":"$gjenlevFNR",
                                  "land":"NO"
                               }
                            ],
                            "foedselsdato":"1971-06-11",
                            "etternavn":"gg",
                            "fornavn":"gg",
                            "kjoenn":"M"
                         }
                      },
                      "trygdetid":[
                         {
                            "sum":{
                               "dager":{
                                  "type":"7",
                                  "nr":"2"
                               },
                               "aar":"4",
                               "maaneder":"11"
                            },
                            "beregning":"111",
                            "type":"10"
                         }
                      ]
                   },
                   "sedGVer":"4",
                   "nav":{
                      "bruker":{
                         "person":{
                            "fornavn":"ss",
                            "pin":[
                               {
                                  "land":"NO",
                                  "identifikator":"$forsikretFnr"
                               }
                            ],
                            "kjoenn":"K",
                            "etternavn":"gg",
                            "foedselsdato":"2009-06-10"
                         }
                      }
                   },
                   "sedVer":"2",
                   "sed":"$sedType"
                }
            """.trimIndent()
        return sed
    }


}