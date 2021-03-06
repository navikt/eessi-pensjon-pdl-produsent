package no.nav.eessi.pensjon.personidentifisering

import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import no.nav.eessi.pensjon.personoppslag.pdl.model.SokKriterier
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskIdentifikasjonsnummer
import java.time.LocalDate

data class IdentifisertPerson(
    val fnr: Fodselsnummer?,
    val uidFraPdl: List<UtenlandskIdentifikasjonsnummer> = emptyList(),
    val aktoerId: String,
    val landkode: String?,
    val geografiskTilknytning: String?,
    val harAdressebeskyttelse: Boolean,
    val personListe: List<IdentifisertPerson>? = null,
    val personRelasjon: SEDPersonRelasjon,
    val erDoed: Boolean = false
) {
    fun flereEnnEnPerson() = false

}

data class UtenlandskPin(
    val kilde: String,
    val identifikasjonsnummer: String,
    val utstederland: String
)

data class SEDPersonRelasjon(
    val fnr: Fodselsnummer?,
    val relasjon: Relasjon,
    val saktype: Saktype? = null,
    val sedType: SedType? = null,
    val sokKriterier: SokKriterier? = null,
    val fdato: LocalDate? = null,
    val rinaDocumentId: String
) {
    fun isFnrDnrSinFdatoLikSedFdato(): Boolean {
        //fdato == null and return true validation allow fnr
        //fdato == null and return false validation fail
        if (fdato == null) return true

        return fnr?.getBirthDate() == fdato
    }


}

enum class Relasjon {
    FORSIKRET,
    GJENLEVENDE,
    AVDOD,
    ANNET,
    BARN,
    FORSORGER
}
