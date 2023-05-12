package no.nav.eessi.pensjon.pdl.identOppdateringGjenlev

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.sed.PinItem
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.oppgave.OppgaveOppslag
import no.nav.eessi.pensjon.pdl.FNR
import no.nav.eessi.pensjon.pdl.IdentBaseTest
import no.nav.eessi.pensjon.pdl.OppgaveModel
import no.nav.eessi.pensjon.pdl.SOME_FNR
import no.nav.eessi.pensjon.pdl.validering.LandspesifikkValidering
import no.nav.eessi.pensjon.pensjonsinformasjon.clients.PensjonsinformasjonClient
import no.nav.eessi.pensjon.pensjonsinformasjon.models.Pensjontype
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class VurderGjenlevOppdateringIdentTest : IdentBaseTest() {

    var euxService: EuxService = mockk(relaxed = true)
    var kodeverkClient: KodeverkClient = mockk(relaxed = true)
    var oppgaveOppslag: OppgaveOppslag = mockk()
    var personService: PersonService = mockk()
    var pensjonsinformasjonClient: PensjonsinformasjonClient = mockk()
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
            pensjonsinformasjonClient,
            landspesifikkValidering
        )
    }

    @Disabled
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
                    )
                )

        every { euxService.hentSed(any(), any()) } returns
                sedGjenlevende(
                    id = SOME_FNR, land = "NO", pinItem = listOf(
                        PinItem(identifikator = "5 12 020-1234", land = "SE"),
                        PinItem(identifikator = SOME_FNR, land = "NO")
                    )
                )

        assertEquals(
            OppgaveModel.Oppdatering(
                "Innsending av endringsmelding",
                pdlEndringsMelding(SOME_FNR, utstederland = "SWE")
            ),
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
        every { pensjonsinformasjonClient.hentKunSakTypeForFnr("123456479867", "11067122781") } returns Pensjontype("1", "GJENLEV")

        assertEquals(
            OppgaveModel.Oppdatering(
                "Innsending av endringsmelding",
                pdlEndringsMelding(FNR, gjenlevUid, utstederland = "DNK")
            ),
            (identoppdatering.vurderUtenlandskGjenlevIdent(sedHendelse(avsenderLand = "DK")))
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
                            "foedselsdato":"1981-02-12",
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