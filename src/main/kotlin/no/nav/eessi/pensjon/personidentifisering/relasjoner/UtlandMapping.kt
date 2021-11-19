package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.Person
import no.nav.eessi.pensjon.personidentifisering.UtenlandskPin

class UtlandMapping {

    fun mapUtenlandsPin(person: Person): List<UtenlandskPin> {

        return person.pin?.filterNot { it.land == "NO" }
            ?.filter { it.land != null && it.identifikator != null && it.institusjonsnavn != null }
            ?.map { UtenlandskPin(it.institusjonsnavn!!, it.identifikator!!, it.land!!) }
            ?: emptyList()
    }
}