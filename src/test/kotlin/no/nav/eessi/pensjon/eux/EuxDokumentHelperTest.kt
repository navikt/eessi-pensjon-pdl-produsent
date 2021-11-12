package no.nav.eessi.pensjon.eux

import io.mockk.clearAllMocks
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.model.sed.P15000
import no.nav.eessi.pensjon.eux.model.sed.R005
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.models.SedHendelseModel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class EuxDokumentHelperTest {

    private val euxKlient: EuxKlient = mockk(relaxed = true)
    private val helper = EuxDokumentHelper(euxKlient)

    @BeforeEach
    fun before() {
        helper.initMetrics()
    }

    @AfterEach
    fun after() {
        clearAllMocks()
    }

    @Test
    fun `Finn korrekt ytelsestype for AP fra sed R005`() {
        val sedR005 = mapJsonToAny(javaClass.getResource("/eux/sed/R_BUC_02-R005-AP.json").readText(), typeRefs<R005>())

        val sedHendelse = SedHendelseModel(rinaSakId = "123456", rinaDokumentId = "1234", sektorKode = "R", bucType =
        BucType.R_BUC_02, rinaDokumentVersjon = "1")

        val seds = listOf(sedR005)
        val actual = helper.hentSaktypeType(sedHendelse, seds)

        assertEquals(Saktype.ALDER ,actual)
    }

    @Test
    fun `Finn korrekt ytelsestype for UT fra sed R005`() {
        val sedR005 = mapJsonToAny(javaClass.getResource("/eux/sed/R_BUC_02-R005-UT.json").readText(), typeRefs<R005>())

        val sedHendelse = SedHendelseModel(rinaSakId = "123456", rinaDokumentId = "1234", sektorKode = "R", bucType =
        BucType.R_BUC_02, rinaDokumentVersjon = "1")

        val seds = listOf(sedR005)

        val actual = helper.hentSaktypeType(sedHendelse, seds)
        assertEquals(Saktype.UFOREP, actual)
    }

    @Test
    fun `Finn korrekt ytelsestype for AP fra sed P15000`() {
        val sedR005 = mapJsonToAny(javaClass.getResource("/eux/sed/R_BUC_02-R005-UT.json").readText(), typeRefs<R005>())
        val sedP15000 = mapJsonToAny(javaClass.getResource("/eux/sed/P15000-NAV.json").readText(), typeRefs<P15000>())

        val sedHendelse = SedHendelseModel(rinaSakId = "123456", rinaDokumentId = "1234", sektorKode = "P", bucType = BucType.P_BUC_10, sedType = SedType.P15000, rinaDokumentVersjon = "1")
        val seds: List<SED> = listOf(
            sedR005,
            sedP15000
        )

        val actual = helper.hentSaktypeType(sedHendelse, seds)
        assertEquals(Saktype.ALDER, actual)
    }

}


