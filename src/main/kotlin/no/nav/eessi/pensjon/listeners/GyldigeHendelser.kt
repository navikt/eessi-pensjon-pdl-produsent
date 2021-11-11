package no.nav.eessi.pensjon.listeners

import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.eux.model.sed.SedType.H020
import no.nav.eessi.pensjon.eux.model.sed.SedType.H021
import no.nav.eessi.pensjon.eux.model.sed.SedType.H070
import no.nav.eessi.pensjon.eux.model.sed.SedType.H120
import no.nav.eessi.pensjon.eux.model.sed.SedType.H121
import no.nav.eessi.pensjon.eux.model.sed.SedType.P10000
import no.nav.eessi.pensjon.eux.model.sed.SedType.P15000
import no.nav.eessi.pensjon.eux.model.sed.SedType.P2000
import no.nav.eessi.pensjon.eux.model.sed.SedType.P2100
import no.nav.eessi.pensjon.eux.model.sed.SedType.P2200
import no.nav.eessi.pensjon.eux.model.sed.SedType.P5000
import no.nav.eessi.pensjon.eux.model.sed.SedType.P6000
import no.nav.eessi.pensjon.eux.model.sed.SedType.P7000
import no.nav.eessi.pensjon.eux.model.sed.SedType.P8000
import no.nav.eessi.pensjon.eux.model.sed.SedType.R005
import no.nav.eessi.pensjon.sed.SedHendelseModel

class GyldigeHendelser {
    companion object {

        private val gyldigeInnkommendeSedType: Set<SedType> = setOf(
            P2000, P2100, P2200, P5000, P6000, P7000, P8000, P10000,
            P15000, H020, H021, H070, H120, H121, R005
        )

        fun mottatt(hendelse: SedHendelseModel) =
                when {
                    hendelse.sedType in gyldigeInnkommendeSedType -> true
                    else -> false
                }
    }
}
