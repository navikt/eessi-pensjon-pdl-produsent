package no.nav.eessi.pensjon.personidentifisering

import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentifisertPerson
import no.nav.eessi.pensjon.personoppslag.pdl.model.Kontaktadresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.SEDPersonRelasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskIdentifikasjonsnummer
import no.nav.eessi.pensjon.shared.person.Fodselsnummer

data class IdentifisertPersonPDL(
    override var aktoerId: String,
    override var landkode: String?,
    override var geografiskTilknytning: String?,
    override var personRelasjon: SEDPersonRelasjon?,
    override val fnr: Fodselsnummer?,
    override var personListe: List<IdentifisertPerson>? = null,
    val uidFraPdl: List<UtenlandskIdentifikasjonsnummer> = emptyList(),
    val harAdressebeskyttelse: Boolean,
    val erDoed: Boolean = false,
    val kontaktAdresse: Kontaktadresse?

) : IdentifisertPerson

data class UtenlandskPin(
    val kilde: String,
    val identifikasjonsnummer: String,
    val utstederland: String
)
