package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer

class Forsikret {
    fun hentForsikretPerson(sed: SED): Fodselsnummer? {
        val forsikretPerson = sed.nav?.bruker?.person?: return null
        logger.info("Leter etter gyldig ident og relasjon(er) i SedType: ${sed.type}")

        return Fodselsnummer.fra(forsikretPerson.pin?.firstOrNull { it.land == "NO" }?.identifikator)
    }
}