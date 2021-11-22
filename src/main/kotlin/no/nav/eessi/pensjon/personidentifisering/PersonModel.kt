package no.nav.eessi.pensjon.personidentifisering

import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskIdentifikasjonsnummer

data class IdentifisertPerson(
    val personIdenterFraSed: PersonIdenter,
    val uidFraPdl: List<UtenlandskIdentifikasjonsnummer> = emptyList()
) {
//    fun validateSedUidAgainstPdlUid(fl: FinnLand) : IdentifisertPerson? {
//        //pdl pair (land, ident)
//        val pdlPair = uidFraPdl.map { Pair(it.utstederland, it.identifikasjonsnummer) }
//
//        //make new seduid validatet against pdluid (contrycode, ident) map use interface FinnLand (iso2->iso3) SE->SWE
//        val newSedUid = personIdenterFraSed.uid
//            .mapNotNull { seduid -> fl.finnLandkode(seduid.utstederland)?.let {  UtenlandskPin(seduid.kilde, seduid.identifikasjonsnummer, it) } }
//            .filterNot { seduid ->
//                //sed pair (land, ident)
//                val seduidPair = Pair( seduid.utstederland , seduid.identifikasjonsnummer)
//                //filter current seduidPair in all pdlPair
//                seduidPair in pdlPair
//            }
//        if (newSedUid.isEmpty()) return null //no new uid to add to pdl
//
//        val newpersonIdenterFraSed = this.personIdenterFraSed.copy(uid = newSedUid)
//        return this.copy(personIdenterFraSed = newpersonIdenterFraSed, uidFraPdl = emptyList()) //new ident with uid not in pdl
//    }

}

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

