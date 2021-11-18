package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.personidentifisering.PersonIdenter
import no.nav.eessi.pensjon.personidentifisering.Rolle
import no.nav.eessi.pensjon.personidentifisering.UtenlandskPin
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer

/**
 * Generic Hjelpe Relasjon klassse for innhenting av ident fra øvrikge SED vi ikke har spesefikkt laget egne klasser for.
 *
 */
class GenericIdent() : AbstractIdent() {

    val forsikret : Forsikret = Forsikret()

    override fun hentRelasjoner(sed: SED): List<PersonIdenter> {
        val fnrListe = mutableListOf<PersonIdenter>()

        leggTilAnnenGjenlevendeFnrHvisFinnes(sed)?.let { annenRelasjon ->
            fnrListe.add(annenRelasjon)
        }
        fnrListe.addAll(forsikret.hentForsikretPerson(sed))

        return fnrListe
    }

    /**
     * P8000-P10000 - [01] Søker til etterlattepensjon
     * P8000-P10000 - [02] Forsørget/familiemedlem
     * P8000-P10000 - [03] Barn
     */
    private fun leggTilAnnenGjenlevendeFnrHvisFinnes(sed: SED): PersonIdenter? {
        val gjenlevendePerson = sed.nav?.annenperson?.takeIf { it.person?.rolle == Rolle.ETTERLATTE.name }?.person

        gjenlevendePerson?.let { person ->
            val fodselnummer = Fodselsnummer.fra(person.pin?.firstOrNull { it.land == "NO" }?.identifikator)

            val pinItemUtlandList = UtlandMapping().mapUtenlandsPin(person)
            return PersonIdenter(fodselnummer, pinItemUtlandList, sedType = sed.type)
        }
        return null
    }
}