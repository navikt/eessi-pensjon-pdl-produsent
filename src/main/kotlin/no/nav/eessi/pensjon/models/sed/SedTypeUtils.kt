package no.nav.eessi.pensjon.models.sed

import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.eux.model.sed.SedType.H001
import no.nav.eessi.pensjon.eux.model.sed.SedType.H002
import no.nav.eessi.pensjon.eux.model.sed.SedType.H020
import no.nav.eessi.pensjon.eux.model.sed.SedType.H021
import no.nav.eessi.pensjon.eux.model.sed.SedType.H070
import no.nav.eessi.pensjon.eux.model.sed.SedType.H120
import no.nav.eessi.pensjon.eux.model.sed.SedType.H121
import no.nav.eessi.pensjon.eux.model.sed.SedType.P10000
import no.nav.eessi.pensjon.eux.model.sed.SedType.P13000
import no.nav.eessi.pensjon.eux.model.sed.SedType.P15000
import no.nav.eessi.pensjon.eux.model.sed.SedType.P2000
import no.nav.eessi.pensjon.eux.model.sed.SedType.P2100
import no.nav.eessi.pensjon.eux.model.sed.SedType.P2200
import no.nav.eessi.pensjon.eux.model.sed.SedType.P5000
import no.nav.eessi.pensjon.eux.model.sed.SedType.P6000
import no.nav.eessi.pensjon.eux.model.sed.SedType.P7000
import no.nav.eessi.pensjon.eux.model.sed.SedType.P8000
import no.nav.eessi.pensjon.eux.model.sed.SedType.R004
import no.nav.eessi.pensjon.eux.model.sed.SedType.R005
import no.nav.eessi.pensjon.eux.model.sed.SedType.R006
import no.nav.eessi.pensjon.eux.model.sed.SedType.X001
import no.nav.eessi.pensjon.eux.model.sed.SedType.X002
import no.nav.eessi.pensjon.eux.model.sed.SedType.X003
import no.nav.eessi.pensjon.eux.model.sed.SedType.X004
import no.nav.eessi.pensjon.eux.model.sed.SedType.X006
import no.nav.eessi.pensjon.eux.model.sed.SedType.X007
import no.nav.eessi.pensjon.eux.model.sed.SedType.X009
import no.nav.eessi.pensjon.eux.model.sed.SedType.X011
import no.nav.eessi.pensjon.eux.model.sed.SedType.X012
import no.nav.eessi.pensjon.eux.model.sed.SedType.X013
import no.nav.eessi.pensjon.eux.model.sed.SedType.X050
import no.nav.eessi.pensjon.eux.model.sed.SedType.X100
import no.nav.eessi.pensjon.models.sed.SedTypeUtils.kanInneholdeIdentEllerFdato
import no.nav.eessi.pensjon.models.sed.SedTypeUtils.ugyldigeTyper

object SedTypeUtils {

    /**
     * SED-typer som kan inneholde ident (fnr/dnr) og/eller fdato
     */
    val kanInneholdeIdentEllerFdato: Set<SedType> = setOf(
        P2000, P2100, P2200, P5000, P6000, P7000, P8000, P10000,
        P15000, H020, H021, H070, H120, H121, R005
    )

    /**
     * SED-typer vi IKKE behandler i Journalføring.
     */
    val ugyldigeTyper: Set<SedType> = setOf(
        P13000, X001, X002, X003, X004, X006, X007, X009,
        X011, X012, X013, X050, X100, H001, H002, H020, H021, H120, H121, R004, R006
    )
}

fun SedType.kanInneholdeIdentEllerFdato(): Boolean = this in kanInneholdeIdentEllerFdato

fun SedType?.erGyldig(): Boolean = this != null && this !in ugyldigeTyper
