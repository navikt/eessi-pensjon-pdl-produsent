package no.nav.eessi.pensjon.pdl.filtrering

import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.personidentifisering.UtenlandskPin
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient

class PdlFiltrering {

    fun filtrerUidSomIkkeFinnesIPdl(identifisertPerson: IdentifisertPerson, kodeverk: KodeverkClient) : IdentifisertPerson? {
        //pdl pair (land, ident)
        val pdlPair = identifisertPerson.uidFraPdl.map { Pair(it.utstederland, it.identifikasjonsnummer) }

        //make new seduid validatet against pdluid (contrycode, ident) map use interface FinnLand (iso2->iso3) SE->SWE
        val newSedUid = identifisertPerson.personIdenterFraSed.uid
            .mapNotNull { seduid -> kodeverk.finnLandkode(seduid.utstederland)?.let {  UtenlandskPin(seduid.kilde, seduid.identifikasjonsnummer, it) } }
            .filterNot { seduid ->
                //sed pair (land, ident)
                val seduidPair = Pair( seduid.utstederland , seduid.identifikasjonsnummer)
                //filter current seduidPair in all pdlPair
                seduidPair in pdlPair
            }
        if (newSedUid.isEmpty()) return null //no new uid to add to pdl

        val newpersonIdenterFraSed = identifisertPerson.personIdenterFraSed.copy(uid = newSedUid)
        return identifisertPerson.copy(personIdenterFraSed = newpersonIdenterFraSed, uidFraPdl = emptyList()) //new ident with uid not in pdl
    }
}