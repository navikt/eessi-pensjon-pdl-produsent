package no.nav.eessi.pensjon.pdl;

import no.nav.eessi.pensjon.models.PdlEndringOpplysning
import no.nav.eessi.pensjon.oppgave.OppgaveData

open class OppgaveModel {

    sealed class Result {
        abstract val description: String
        abstract val metricTagValueOverride: String?

        val metricTagValue: String
        get() = metricTagValueOverride ?: description
    }

    data class Oppdatering(override val description: String, val pdlEndringsOpplysninger: PdlEndringOpplysning, override val metricTagValueOverride: String? = null, ): Result()
    data class IngenOppdatering(override val description: String, override val metricTagValueOverride: String? = null): Result()
    data class Oppgave(override val description: String, val oppgaveData: OppgaveData, override val metricTagValueOverride: String? = null): Result()

    data class OppgaveGjenlev(override val description: String, val oppgaveData: OppgaveData, override val metricTagValueOverride: String? = null): Result()

}
