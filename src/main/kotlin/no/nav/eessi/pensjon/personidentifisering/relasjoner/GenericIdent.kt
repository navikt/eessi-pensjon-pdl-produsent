package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.personidentifisering.Rolle
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer

/**
 * Generic Hjelpe Relasjon klassse for innhenting av ident fra øvrige SED vi ikke har spesefikkt laget egne klasser for.
 *
 */
class GenericIdent() : AbstractIdent() {

    val forsikret : Forsikret = Forsikret()

    override fun hentRelasjoner(sed: SED): List<Fodselsnummer?> {
        val fnrListe = mutableListOf<Fodselsnummer?>()

        leggTilAnnenGjenlevendeFnrHvisFinnes(sed)?.let { annenRelasjon ->
            fnrListe.add(annenRelasjon)
        }
        fnrListe.add(forsikret.hentForsikretPerson(sed))

        return fnrListe
    }

    /**
     * P8000-P10000 - [01] Søker til etterlattepensjon
     * P8000-P10000 - [02] Forsørget/familiemedlem
     * P8000-P10000 - [03] Barn
     */
    private fun leggTilAnnenGjenlevendeFnrHvisFinnes(sed: SED): Fodselsnummer? {
        val gjenlevendePerson = sed.nav?.annenperson?.takeIf { it.person?.rolle == Rolle.ETTERLATTE.name }?.person

        gjenlevendePerson?.let { person ->
            return Fodselsnummer.fra(person.pin?.firstOrNull { it.land == "NO" }?.identifikator)
        }
        return null
    }

}