package no.nav.eessi.pensjon.personidentifisering

import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskIdentifikasjonsnummer

data class IdentifisertPerson(
    val fnr: Fodselsnummer?,
    val uidFraPdl: List<UtenlandskIdentifikasjonsnummer> = emptyList()
)

data class UtenlandskPin(
    val kilde: String,
    val identifikasjonsnummer: String,
    val utstederland: String
)

