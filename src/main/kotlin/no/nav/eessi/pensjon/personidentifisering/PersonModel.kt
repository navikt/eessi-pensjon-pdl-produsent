package no.nav.eessi.pensjon.personidentifisering

import no.nav.eessi.pensjon.eux.model.sed.PinItem
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer

data class IdentifisertPerson(
    val personNavn: String?,                                        //fra PDL
    val personRelasjon: PersonIdentier,                          //fra SED
    val fdato: String? = personRelasjon?.fnr?.getBirthDateAsIso()   //fra sed/pdl
) {
    override fun toString(): String {
        return "IdentifisertPerson(personNavn=$personNavn, personRelasjon=$personRelasjon, uid=${personRelasjon.uid})"
    }
}

data class PersonIdentier(
    val fnr: Fodselsnummer?,
    val uid: List<PinItem>? = null,
    val sedType: SedType? = null,
)
