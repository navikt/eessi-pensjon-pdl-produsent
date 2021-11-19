package no.nav.eessi.pensjon.pdl.integrajontest

import io.mockk.verify
import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.eux.model.sed.P2000
import no.nav.eessi.pensjon.eux.model.sed.P8000
import no.nav.eessi.pensjon.eux.model.sed.PinItem
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.models.SedHendelseModel
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskIdentifikasjonsnummer
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@DisplayName("P_BUC_01 – Innkommende Test med UID SWE, Sweden")
internal class IntegrasjonsMottattNyUIDTest : MottattHendelseBase() {

    @Test
    fun `Gitt en mottatt hendelse med uid i sed saa skal du få en liste med identifiserte personer der uid samsvarer med uid fra pdl`() {
        val hendelse = SedHendelseModel.fromJson(createHendelseJson(SedType.P2000, BucType.P_BUC_01, avsenderLand = "SE"))
        val uid = "1236549875456544"

        val pin = listOf(PinItem(identifikator = FNR_VOKSEN, land = "NO"), PinItem(identifikator = uid,land = hendelse.avsenderLand , institusjonsnavn = "important institution"))

        val sed = SED.generateSedToClass<P2000>(createSed(SedType.P2000, pin = pin))

        val utenlandskIdentifikasjonsnummer = listOf(UtenlandskIdentifikasjonsnummer(uid, "SWE", false, null, createMetadata()))

        testRunner(
            fnr = FNR_VOKSEN,
            sed = sed,
            hendelse = hendelse,
            uid = utenlandskIdentifikasjonsnummer
        ) {
            assertNotNull(it)
            val identSe = it.first()
            assertEquals(uid, identSe.personIdenterFraPdl.uid.firstOrNull { it.utstederland == "SE" }?.identifikasjonsnummer)
            assertEquals(uid, identSe.uidFraPdl.first().identifikasjonsnummer)
            assertEquals("SWE", identSe.uidFraPdl.first().utstederland)
            assertNotNull(identSe.uidFraPdl.firstOrNull { it.identifikasjonsnummer == uid &&  it.utstederland == "SWE" && !it.opphoert })
        }

        verify(exactly = 1) {  }
    }

    @Test
    fun `Mottatt hendelse med annen person med Uid`() {
        val hendelse = SedHendelseModel.fromJson(createHendelseJson(SedType.P8000, BucType.P_BUC_02, avsenderLand = "SE"))
        val uid = "1236549875456544"

        val pinForsikretperson = listOf(PinItem(identifikator = FNR_VOKSEN, land = "NO"), PinItem(identifikator = uid,land = hendelse.avsenderLand ))
        val pinannenperson = listOf(PinItem(identifikator = FNR_VOKSEN_2, land = "NO"), PinItem(identifikator = uid+1,land = hendelse.avsenderLand ))

        val sed = SED.generateSedToClass<P8000>(createSed(SedType.P8000, pin = pinForsikretperson, annenPerson = createAnnenPerson(pin = pinannenperson)))

        testRunnerMedAnnenPerson(
            fnr = FNR_VOKSEN,
            annenpersonFnr = FNR_VOKSEN_2,
            sed = sed,
            hendelse = hendelse
        ) {
            assertNotNull(it)
            val identSe = it.first()
            assertEquals(2, it.size)
            validateSedMottattListenerLoggingMessage("Acket sedMottatt melding med")
//            assertEquals("1236549875456544", identSe.personRelasjon.uid?.firstOrNull { it.land == "SE" }?.identifikator)
        }
    }

    @Test
    fun `Mottar en hendelse av typen P3000_UK som ikke er gyldig og dermed ikke evaluert`() {
        val hendelse = SedHendelseModel.fromJson(createHendelseJson(SedType.P3000_UK, BucType.P_BUC_01, avsenderLand = "SE"))
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

    @Test
    fun `mottatt hendelse sed uten norsk ident avsluttes med ack`() {
        val hendelse = SedHendelseModel.fromJson(createHendelseJson(SedType.P2000, BucType.P_BUC_01, avsenderLand = "SE"))
        val uid = "1236549875456544"

        val pin = listOf(PinItem(identifikator = FNR_VOKSEN, land = "SE"), PinItem(identifikator = uid,land = hendelse.avsenderLand , institusjonsnavn = "important institution"))

        val sed = SED.generateSedToClass<P2000>(createSed(SedType.P2000, pin = pin))

        val utenlandskIdentifikasjonsnummer = listOf(UtenlandskIdentifikasjonsnummer(uid, "SWE", false, null, createMetadata()))

        testRunner(
            fnr = null,
            sed = sed,
            hendelse = hendelse,
            uid = utenlandskIdentifikasjonsnummer
        ) {
            assertNotNull(it)
            assertEquals(emptyList(), it)
            validateSedMottattListenerLoggingMessage("Ingen identifiserte personer funnet Acket sedMottatt")
        }
    }

}