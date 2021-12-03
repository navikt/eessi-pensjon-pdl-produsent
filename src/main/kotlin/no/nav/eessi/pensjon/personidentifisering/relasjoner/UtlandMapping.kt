package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.Person
import no.nav.eessi.pensjon.personidentifisering.UtenlandskPin

class UtlandMapping {

    fun mapUtenlandsPin(person: Person): List<UtenlandskPin> {

        return person.pin?.filterNot { it.land == "NO" }
            ?.filter { it.land != null && it.identifikator != null && it.institusjon?.institusjonsnavn != null }
            ?.map { UtenlandskPin(it.institusjon?.institusjonsnavn!!, it.identifikator!!, it.land!!) }
            ?: emptyList()
    }
}