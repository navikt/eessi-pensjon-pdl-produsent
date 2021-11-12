package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.P5000
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.personidentifisering.PersonIdentier

class P5000Ident() : GjenlevendeHvisFinnes() {

    override fun hentRelasjoner(sed: SED): List<PersonIdentier> {
        val forsikret = hentForsikretPerson(sed)
        val gjenlevende = hentRelasjonGjenlevendeFnrHvisFinnes((sed as P5000).p5000Pensjon?.gjenlevende, sed.type)

        return gjenlevende.ifEmpty { forsikret }

    }

}