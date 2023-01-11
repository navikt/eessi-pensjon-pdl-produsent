package no.nav.eessi.pensjon.pdl.validering

import no.nav.eessi.pensjon.eux.model.BucType.H_BUC_07
import no.nav.eessi.pensjon.eux.model.BucType.R_BUC_02
import no.nav.eessi.pensjon.eux.model.SedHendelse

fun erRelevantForEESSIPensjon(hendelse: SedHendelse) =
    when {
        hendelse.bucType == null -> false
        hendelse.sedType == null -> false
        hendelse.sedType!!.name.startsWith("X") -> false
        hendelse.bucType in listOf(H_BUC_07, R_BUC_02) -> true
        hendelse.sektorKode == "P" -> true
        else -> false
    }
