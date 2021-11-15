package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.personidentifisering.PersonIdenter

class P2200Ident() : AbstractIdent() {

    override fun hentRelasjoner(sed: SED): List<PersonIdenter> = hentForsikretPerson(sed)

}