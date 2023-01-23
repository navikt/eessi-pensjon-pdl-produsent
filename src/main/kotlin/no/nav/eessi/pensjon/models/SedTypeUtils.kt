package no.nav.eessi.pensjon.models

import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.SedType.*
import no.nav.eessi.pensjon.models.SedTypeUtils.ugyldigeTyper

object SedTypeUtils {

    /**
     * SED-typer vi IKKE behandler i Journalføring.
     */
    val ugyldigeTyper: Set<SedType> = setOf(
        P13000, X001, X002, X003, X004, X006, X007, X009,
        X011, X012, X013, X050, X100, H001, H002, H020, H021, H120, H121, R004, R006
    )
}

fun SedType?.erGyldig(): Boolean = this != null && this !in ugyldigeTyper
