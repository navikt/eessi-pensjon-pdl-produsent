package no.nav.eessi.pensjon.pdl.identOppdateringGjenlev

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.sed.PinItem
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.oppgave.OppgaveOppslag
import no.nav.eessi.pensjon.pdl.FNR
import no.nav.eessi.pensjon.pdl.IdentBaseTest
import no.nav.eessi.pensjon.pdl.SOME_FNR
import no.nav.eessi.pensjon.pdl.validering.LandspesifikkValidering
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VurderGjenlevOppdateringIdentTest : IdentBaseTest() {

    var euxService: EuxService = mockk(relaxed = true)
    var kodeverkClient: KodeverkClient = mockk(relaxed = true)
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
            landspesifikkValidering
        )
    }

    @Test
    fun `Gitt at vi har en endringsmelding med en svensk uid, med riktig format saa skal det opprettes en endringsmelding`() {
        every { personService.hentPerson(NorskIdent(FNR)) } returns
                personFraPDL(id = FNR).copy(identer = listOf(IdentInformasjon(FNR, IdentGruppe.FOLKEREGISTERIDENT)))

        every { personService.hentPerson(NorskIdent(SOME_FNR)) } returns
                personFraPDL(id = SOME_FNR).copy(identer = listOf(IdentInformasjon(SOME_FNR, IdentGruppe.FOLKEREGISTERIDENT)))

        every { euxService.hentSed(any(), any()) } returns
                sedGjenlevende(
                    id = SOME_FNR, land = "NO", pinItem = listOf(
                        PinItem(identifikator = "5 12 020-1234", land = "SE"),
                        PinItem(identifikator = SOME_FNR, land = "NO")
                    )
                )

        assertEquals(
            VurderGjenlevOppdateringIdent.Oppdatering(
                "Innsending av endringsmelding",
                pdlEndringsMelding(SOME_FNR, utstederland = "SWE")
            ),
            (identoppdatering.vurderUtenlandskGjenlevIdent(sedHendelse(avsenderLand = "SE")))
        )
    }
}