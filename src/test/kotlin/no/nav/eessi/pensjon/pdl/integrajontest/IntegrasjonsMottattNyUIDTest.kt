package no.nav.eessi.pensjon.pdl.integrajontest

import no.nav.eessi.pensjon.eux.model.sed.P2000
import no.nav.eessi.pensjon.eux.model.sed.PinItem
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.models.BucType.P_BUC_01
import no.nav.eessi.pensjon.models.SedHendelseModel
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@DisplayName("P_BUC_01 – Innkommende Test med UID SWE, Sweden")
internal class IntegrasjonsMottattNyUIDTest : MottattHendelseBase() {

    @Test
    fun mottattHendelseMedUid() {
        val hendelse = SedHendelseModel.fromJson(createHendelseJson(SedType.P2000, P_BUC_01, avsenderLand = "SE"))
        val uid = "1236549875456544"

        val pin = listOf(PinItem(identifikator = FNR_VOKSEN, land = "NO"), PinItem(identifikator = uid,land = hendelse.avsenderLand ))

        val sed = SED.generateSedToClass<P2000>(createSed(SedType.P2000, pin = pin))

        testRunner(
            fnr = FNR_VOKSEN,
            sed = sed,
            hendelse = hendelse
        ) {
            assertNotNull(it)
            println("*".repeat(100))
            println(it)
            println("*".repeat(100))
            val identSe = it.first()
            assertEquals("1236549875456544", identSe.personRelasjon.uid?.firstOrNull { it.land == "SE" }?.identifikator)
        }
    }

    @Test
    fun `Mottar en hendelse av typen P3000_UK som ikke er gyldig og dermed ikke evaluert`() {
        val hendelse = SedHendelseModel.fromJson(createHendelseJson(SedType.P3000_UK, P_BUC_01, avsenderLand = "SE"))
        val uid = "1236549875456544"

        val pin = listOf(PinItem(identifikator = FNR_VOKSEN, land = "NO"), PinItem(identifikator = uid,land = hendelse.avsenderLand ))

        val sed = SED.generateSedToClass<SED>(createSed(SedType.P3000_UK, pin = pin))

        testRunner(
            fnr = FNR_VOKSEN,
            sed = sed,
            hendelse = hendelse
        ) {
            assertNull(it)
        }
    }
}