package no.nav.eessi.pensjon.pdl.integrajontest

import no.nav.eessi.pensjon.eux.model.sed.P2000
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import org.junit.jupiter.api.Test

internal class IntegrasjonsMottattNyUIDTest : MottattHendelseBase() {

    @Test
    fun mottattHendelseMedUid() {
        val hendelse = createHendelseJson(SedType.P2000)
        val sed = SED.generateSedToClass<P2000>(createSed(SedType.P8000, FNR_VOKSEN))


        testRunnerFlerePersoner(
            fnr = FNR_VOKSEN,
            uid = "1236549875456544",
            sed = sed,


        )
    }
}