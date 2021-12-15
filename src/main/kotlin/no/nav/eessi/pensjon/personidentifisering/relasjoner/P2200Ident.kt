package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer

class P2200Ident() : AbstractIdent() {

    private val forsikret : Forsikret = Forsikret()

    override fun hentRelasjoner(sed: SED): List<Fodselsnummer?> = listOf(forsikret.hentForsikretPerson(sed))

}