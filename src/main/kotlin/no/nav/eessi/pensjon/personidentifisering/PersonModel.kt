package no.nav.eessi.pensjon.personidentifisering

import no.nav.eessi.pensjon.eux.model.sed.PinItem
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import no.nav.eessi.pensjon.personoppslag.pdl.model.KjoennType
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskIdentifikasjonsnummer

data class IdentifisertPerson(
    val personNavn: String?,                                        //fra PDL
    val personIdenter: PersonIdenter,                             //fra SED
    val fdato: String? = personIdenter.fnr?.getBirthDateAsIso(),     //fra sed/pdl
    val kjoenn: KjoennType? = null,
    val uid: List<UtenlandskIdentifikasjonsnummer> = emptyList()
) {
    override fun toString(): String {
        return "IdentifisertPerson(personNavn=$personNavn, personRelasjon=$personIdenter, uid=${personIdenter.uid})"
    }
}

data class PersonIdenter(
    val fnr: Fodselsnummer?,
    val uid: List<PinItem>? = null,
    val sedType: SedType? = null,
)

data class PersonIdentValidering(
    val identifikasjonsnummer: String,    //verdi fra SED
    val landkode3: String? = null,        //verdi fra Kodeverket
)

