package no.nav.eessi.pensjon.personidentifisering

import no.nav.eessi.pensjon.eux.model.sed.PinItem
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer

data class IdentifisertPerson(
    val aktoerId: String,                               //fra PDL
    val personNavn: String?,                            //fra PDL
    val landkode: String?,                              //fra PDL
    val personRelasjon: SEDPersonRelasjon,              //fra PDL
    val fodselsdato: String? = null,                    //innhenting fra FnrHelper og SED
) {
    override fun toString(): String {
        return "IdentifisertPerson(aktoerId='$aktoerId', personNavn=$personNavn, landkode=$landkode, personRelasjon=$personRelasjon, uid=${personRelasjon.uid})"
    }
}

data class SEDPersonRelasjon(
    val fnr: Fodselsnummer?,
    val uid: List<PinItem>? = null,
    val relasjon: Relasjon,
    val saktype: Saktype? = null,
    val sedType: SedType? = null,
)

enum class Relasjon {
    FORSIKRET,
    GJENLEVENDE,
    AVDOD,
    ANNET,
    BARN,
    FORSORGER
}
