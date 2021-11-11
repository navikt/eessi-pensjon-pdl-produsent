package no.nav.eessi.pensjon.pdl.integrajontest

import no.nav.eessi.pensjon.eux.model.sed.P2000
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.sed.SedHendelseModel
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("P_BUC_01 – Innkommende Test med UID SWE, Sweden")
internal class IntegrasjonsMottattNyUIDTest : MottattHendelseBase() {

    @Test
    fun mottattHendelseMedUid() {
        val hendelseJson = createHendelseJson(SedType.P2000)
        val sed = SED.generateSedToClass<P2000>(createSed(SedType.P2000, FNR_VOKSEN))

        testRunner(
            fnr = FNR_VOKSEN,
            uid = "1236549875456544",
            sed = sed,
            hendelse = SedHendelseModel.fromJson(hendelseJson)
        ) {
            //assertEquals("2342342", it.aktoerId)
        }
    }
}