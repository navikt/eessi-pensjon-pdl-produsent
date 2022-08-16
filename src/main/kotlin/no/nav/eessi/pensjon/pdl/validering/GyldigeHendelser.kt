package no.nav.eessi.pensjon.pdl.validering

import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.models.SedHendelseModel

class GyldigeHendelser {
    companion object {
        private const val gyldigSektorKode = "P"
        private val gyldigeInnkommendeBucTyper = listOf(BucType.H_BUC_07, BucType.R_BUC_02)

        fun mottatt(hendelse: SedHendelseModel) =
            when {
                hendelse.bucType == null -> false
                hendelse.sedType == null -> false
                hendelse.sedType.name.startsWith("X") -> false
                hendelse.bucType in gyldigeInnkommendeBucTyper -> true
                hendelse.sektorKode == gyldigSektorKode -> true
                else -> false
            }
    }

}
