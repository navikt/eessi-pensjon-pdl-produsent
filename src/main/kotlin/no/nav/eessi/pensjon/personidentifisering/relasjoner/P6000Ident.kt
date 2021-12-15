package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.P6000
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer

class P6000Ident() : AbstractIdent() {

    private val gjenlevende: Gjenlevende = Gjenlevende()
    private val forsikret: Forsikret = Forsikret()

    override fun hentRelasjoner(sed: SED): List<Fodselsnummer?> {
        val forsikret = forsikret.hentForsikretPerson(sed)
        val gjenlevende =
            gjenlevende.hentRelasjonGjenlevendeFnrHvisFinnes((sed as P6000).p6000Pensjon?.gjenlevende, sed.type)

        return listOf(gjenlevende).ifEmpty { listOf(forsikret) }
    }
}