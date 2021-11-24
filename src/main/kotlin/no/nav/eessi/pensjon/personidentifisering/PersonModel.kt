package no.nav.eessi.pensjon.personidentifisering

import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskIdentifikasjonsnummer

data class IdentifisertPerson(
    val personIdenterFraSed: PersonIdenter,
    val uidFraPdl: List<UtenlandskIdentifikasjonsnummer> = emptyList()
)

data class PersonIdenter(
    val fnr: Fodselsnummer?,
    val uid: List<UtenlandskPin> = emptyList()

) {
    /**
     * @return true dersom uid fra sed er lik uid fra pdl
     * Sjekker om uid fra sed er lik uid i pdl.
     */
    fun finnesAlleredeIPDL(alleUidIPDL: List<String>) : Boolean {
        val alleUidFraSed = uid.map { it.identifikasjonsnummer }
        return  alleUidIPDL.any { it in alleUidFraSed }
    }
}

data class UtenlandskPin(
    val kilde: String,
    val identifikasjonsnummer: String,
    val utstederland: String
)

