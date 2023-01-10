package no.nav.eessi.pensjon.pdl.validering

import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.buc.BucType

fun erRelevantForEESSIPensjon(hendelse: SedHendelse) =
    when {
        hendelse.bucType == null -> false
        hendelse.sedType == null -> false
        hendelse.sedType!!.name.startsWith("X") -> false
        hendelse.bucType in listOf(BucType.H_BUC_07, BucType.R_BUC_02) -> true
        hendelse.sektorKode == "P" -> true
        else -> false
    }
