package no.nav.eessi.pensjon.pdl

import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.eux.model.sed.Institusjon
import no.nav.eessi.pensjon.eux.model.sed.P2000
import no.nav.eessi.pensjon.eux.model.sed.P8000
import no.nav.eessi.pensjon.eux.model.sed.PinItem
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.models.SedHendelseModel
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskIdentifikasjonsnummer
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertNull

@DisplayName("P_BUC_01 – Innkommende Test med UID SWE, Sweden")
internal class MottattHendelseTest : MottattHendelseBase() {

    @Test
    fun `Gitt en mottatt hendelse med uid i sed saa skal du få en liste med identifiserte personer der uid samsvarer med uid fra pdl`() {
        val hendelse = SedHendelseModel.fromJson(createHendelseJson(SedType.P2000, BucType.P_BUC_01, avsenderLand = "SE"))
        val uid = "1236549875456544"

        val pin = listOf(PinItem(identifikator = FNR_VOKSEN, land = "NO"), PinItem(identifikator = uid,land = hendelse.avsenderLand , institusjon =  Institusjon(institusjonsnavn = "NAVNO", institusjonsid = "123")))

        val sed = SED.generateSedToClass<P2000>(createSed(SedType.P2000, pin = pin))

        val buc  = mapJsonToAny(javaClass.getResource("/eux/buc/buc279020.json")!!.readText(), typeRefs<Buc>())

        val utenlandskIdentifikasjonsnummer = listOf(UtenlandskIdentifikasjonsnummer(uid, "SWE", false, null, createMetadata()))

        testRunner(
            fnr = FNR_VOKSEN,
            sed = sed,
            buc = buc,
            hendelse = hendelse,
            uid = utenlandskIdentifikasjonsnummer
        ) {

        }
    }

    @Test
    fun `Gitt en buc med flere identifiserte personer saa utføres ikke oppdatering av PDL`() {
        val hendelse = SedHendelseModel.fromJson(createHendelseJson(SedType.P8000, BucType.P_BUC_02, avsenderLand = "SE"))
        val uid = "1236549875456544"

        val pinForsikretperson = listOf(PinItem(identifikator = FNR_VOKSEN, land = "NO"), PinItem(identifikator = uid,land = hendelse.avsenderLand ))
        val pinannenperson = listOf(PinItem(identifikator = FNR_VOKSEN_2, land = "NO"), PinItem(identifikator = uid+1,land = hendelse.avsenderLand ))

        val sed = SED.generateSedToClass<P8000>(createSed(SedType.P8000, pin = pinForsikretperson, annenPerson = createAnnenPerson(pin = pinannenperson)))
        val buc  = mapJsonToAny(javaClass.getResource("/eux/buc/buc279020.json").readText(), typeRefs<Buc>())

        testRunnerMedAnnenPerson(
            fnr = FNR_VOKSEN,
            annenpersonFnr = FNR_VOKSEN_2,
            sed = sed,
            buc = buc,
            hendelse = hendelse
        ) {
        }
    }

    @Test
    fun `Mottar en hendelse av typen P3000_UK som ikke er gyldig og dermed ikke evaluert`() {
        val hendelse = SedHendelseModel.fromJson(createHendelseJson(SedType.P3000_UK, BucType.P_BUC_01, avsenderLand = "SE"))
        val uid = "1236549875456544"

        val pin = listOf(PinItem(identifikator = FNR_VOKSEN, land = "NO"), PinItem(identifikator = uid,land = hendelse.avsenderLand ))

        val sed = SED.generateSedToClass<SED>(createSed(SedType.P3000_UK, pin = pin))
        val buc  = mapJsonToAny(javaClass.getResource("/eux/buc/buc279020.json").readText(), typeRefs<Buc>())

        testRunner(
            fnr = FNR_VOKSEN,
            sed = sed,
            buc = buc,
            hendelse = hendelse
        ) {
            assertNull(it)
        }
    }

    @Test
    fun `mottatt hendelse sed uten norsk ident avsluttes med ack`() {
        val hendelse = SedHendelseModel.fromJson(createHendelseJson(SedType.P2000, BucType.P_BUC_01, avsenderLand = "SE"))
        val uid = "1236549875456544"

        val pin = listOf(PinItem(identifikator = FNR_VOKSEN, land = "SE"), PinItem(identifikator = uid,land = hendelse.avsenderLand , institusjon =  Institusjon(institusjonsnavn = "NAVSE", institusjonsid = "123")))

        val sed = SED.generateSedToClass<P2000>(createSed(SedType.P2000, pin = pin))
        val buc  = mapJsonToAny(javaClass.getResource("/eux/buc/buc279020.json").readText(), typeRefs<Buc>())

        val utenlandskIdentifikasjonsnummer = listOf(UtenlandskIdentifikasjonsnummer(uid, "SWE", false, null, createMetadata()))

        testRunner(
            fnr = null,
            sed = sed,
            buc = buc,
            hendelse = hendelse,
            uid = utenlandskIdentifikasjonsnummer
        ) {
            assertNull(it)
            validateSedMottattListenerLoggingMessage("Ingen identifiserte personer funnet Acket sedMottatt")
        }
    }

    @Test
    fun `Gitt en BUC med flere UID så avsluttes med ack`() {
        val hendelse = SedHendelseModel.fromJson(createHendelseJson(SedType.P2000, BucType.P_BUC_01, avsenderLand = "SE"))
        val uid = "1236549875456544"

        val pin = listOf(PinItem(identifikator = FNR_VOKSEN, land = "SE"), PinItem(identifikator = uid,land = hendelse.avsenderLand , institusjon =  Institusjon(institusjonsnavn = "NAVSE", institusjonsid = "123")))

        val sed = SED.generateSedToClass<P2000>(createSed(SedType.P2000, pin = pin))
        val buc  = mapJsonToAny(javaClass.getResource("/eux/buc/buc279020.json").readText(), typeRefs<Buc>())

        val utenlandskIdentifikasjonsnummer = listOf(
            UtenlandskIdentifikasjonsnummer(uid, "SWE", false, null, createMetadata()),
            UtenlandskIdentifikasjonsnummer(uid, "DKK", false, null, createMetadata())
        )

        testRunner(
            fnr = null,
            sed = sed,
            buc = buc,
            hendelse = hendelse,
            uid = utenlandskIdentifikasjonsnummer
        ) {
            validateSedMottattListenerLoggingMessage("Antall utenlandske IDer er flere enn en")
        }
    }

}