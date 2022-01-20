package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.eux.model.sed.R005
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.typeRefs
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class R005RelasjonTest : RelasjonTestBase(){

    @Test
    fun `Gitt personer med rolle ANNET når personrelasjoner velges så ignorer disse`() {
        val forsikretFnr = SLAPP_SKILPADDE
        val annenPersonFnr = KRAFTIG_VEGGPRYD

        val r005 = createR005(
            forsikretFnr = forsikretFnr, forsikretTilbakekreving = "debitor",
            annenPersonFnr = annenPersonFnr, annenPersonTilbakekreving = "debitor"
        )
        val actual = R005Relasjon(r005, BucType.R_BUC_02, "" +
                "654sdfguhdfigh").hentRelasjoner()

        Assertions.assertEquals(0, actual.size)
    }

    @Test
    fun `Gitt et gyldig fnr og relasjon avdod så skal det identifiseres en person`() {
        val gjenlevFnr = LEALAUS_KAKE

        val sedjson = createR005(
            forsikretFnr = SLAPP_SKILPADDE, forsikretTilbakekreving = "avdød_mottaker_av_ytelser",
            annenPersonFnr = gjenlevFnr, annenPersonTilbakekreving = "enke_eller_enkemann"
        ).toJson()

        val sed = mapJsonToAny(sedjson, typeRefs<R005>())

        val relasjon = R005Relasjon(sed, BucType.R_BUC_02, "" +
                "654sdfguhdfigh").hentRelasjoner()

        assertEquals(2, relasjon.size)
    }
}