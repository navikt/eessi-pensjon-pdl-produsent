package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.personidentifisering.PersonIdenter

class P2100Ident() : GjenlevendeHvisFinnes() {

    override fun hentRelasjoner(sed: SED): List<PersonIdenter> = hentRelasjonGjenlevendeFnrHvisFinnes(sed.pensjon?.gjenlevende, sed.type)

}