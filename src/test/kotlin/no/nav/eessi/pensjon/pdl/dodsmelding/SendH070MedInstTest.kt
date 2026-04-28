package no.nav.eessi.pensjon.pdl.dodsmelding

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.OpprettH070.OpprettH070
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.klient.EuxKlientLib
import no.nav.eessi.pensjon.eux.model.buc.SakStatus
import no.nav.eessi.pensjon.eux.model.buc.SakStatus.LOPENDE
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.buc.SakType.UFOREP
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.klienter.saf.SafClient
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.pdl.FNR
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val SAKSID = "1456541"
private const val DOK_ID = "edb72a2474ac4d6f901c74e614da9b5b"

class SendH070MedInstTest {

    private val fagmodulKlient = mockk<FagmodulKlient>()
    private val safClient = mockk<SafClient>()
    private val personService = mockk<PersonService>()
    private val opprettH070 = mockk<OpprettH070>()
    private val euxKlient = mockk<EuxKlientLib>()

    private lateinit var dodsmeldingBehandler: DodsmeldingBehandler
    private lateinit var euxService: EuxService

    @BeforeEach
    fun setup() {
        euxService = EuxService(euxKlient)
        dodsmeldingBehandler = DodsmeldingBehandler(fagmodulKlient, safClient, personService, opprettH070, euxService, "q2")
    }

    @Test
    fun test() {

        val h070 = mockH070(""""FI"""")

        every { euxKlient.createHBuc07(any(), any()) } returns "{\"caseId\":\"$SAKSID\",\"documentId\":\"$DOK_ID\"}"
        every { fagmodulKlient.hentPensjonSaklist(any()) } returns listOf(SakInformasjon(SAKSID, SakType.ALDER, LOPENDE))

        val response = dodsmeldingBehandler.institusjon(FNR, setOf("FIN"))
        val response2 = euxService.opprettH070(FNR, h070)

        assertEquals("FI:0200000010", response)
        assertEquals(EuxService.SaksDetaljer(SAKSID, "$DOK_ID"), response2)
    }

    @Test
    fun `Dersom h070 skal sendes til Sverige og den døde har hatt en ufoere sak på seg saa skal h070 sendes til svensk institusjon nummer2`() {

        val h070 = mockH070(""""SE"""")

        every { euxKlient.createHBuc07(any(), any()) } returns "{\"caseId\":\"$SAKSID\",\"documentId\":\"$DOK_ID\"}"
        every { fagmodulKlient.hentPensjonSaklist(any()) } returns listOf(SakInformasjon(SAKSID, UFOREP, LOPENDE))

       val response = dodsmeldingBehandler.institusjon(FNR, setOf("SWE"))
       euxService.opprettH070(FNR, h070)

        assertEquals("SE:2001", response)
    }

    @Test
    fun `Dersom h070 skal sendes til Sverige og den døde har hatt en alder sak på seg saa skal h070 sendes til svensk institusjon nummer1`() {

        val h070 = mockH070(""""SE"""")

        every { euxKlient.createHBuc07(any(), any()) } returns "{\"caseId\":\"$SAKSID\",\"documentId\":\"$DOK_ID\"}"
        every { fagmodulKlient.hentPensjonSaklist(any()) } returns listOf(SakInformasjon(SAKSID, SakType.ALDER, LOPENDE))

        val response = dodsmeldingBehandler.institusjon(FNR, setOf("SWE"))
        euxService.opprettH070(FNR, h070)

        assertEquals("SE:3002", response)
    }


    private fun mockH070(land: String? = "FI"): SED = mapJsonToAny<SED>(
        """
                {
                  "sed" : "H070",
                  "nav" : {
                    "bruker" : {
                      "doedsfall" : {
                        "doedsdato" : "2024-05-01"
                      },
                      "person" : {
                        "pin" : [ {
                          "institusjonsnavn" : null,
                          "institusjonsid" : null,
                          "sektor" : null,
                          "identifikator" : "12345678901",
                          "land" : "NO",
                          "institusjon" : null
                        }, {
                          "institusjonsnavn" : null,
                          "institusjonsid" : null,
                          "sektor" : null,
                          "identifikator" : "SE1234567890",
                          "land" : $land,
                          "institusjon" : null
                        } ],
                        "pinland" : null,
                        "etternavn" : "Lenger",
                        "etternavnvedfoedsel" : null,
                        "fornavn" : "Lever",
                        "kjoenn" : "K",
                        "foedselsdato" : "1950-10-10",
                        "doedsdato" : null
                      }
                    }
                  },
                  "sedGVer" : "4",
                  "sedVer" : "4",
                  "pensjon" : null
                }
            """.trimIndent()
        )

}
