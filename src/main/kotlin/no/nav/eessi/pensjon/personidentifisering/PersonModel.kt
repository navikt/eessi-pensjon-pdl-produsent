package no.nav.eessi.pensjon.personidentifisering

import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import no.nav.eessi.pensjon.personoppslag.pdl.model.KjoennType
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskIdentifikasjonsnummer

data class IdentifisertPerson(
    val personIdenterFraPdl: PersonIdenter,
    val uidFraPdl: List<UtenlandskIdentifikasjonsnummer> = emptyList()
)

data class PersonIdenter(
    val fnr: Fodselsnummer?,
    val uid: List<UtenlandskPin>? = null,
)

data class UtenlandskPin(
    val kilde: String,
    val identifikasjonsnummer: String,
    val utstederland: String
)

