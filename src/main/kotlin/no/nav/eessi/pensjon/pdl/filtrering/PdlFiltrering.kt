package no.nav.eessi.pensjon.pdl.filtrering

import no.nav.eessi.pensjon.eux.UtenlandskId
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.personidentifisering.UtenlandskPin
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskIdentifikasjonsnummer
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient
import org.springframework.stereotype.Component


@Component
class PdlFiltrering(private val kodeverk: KodeverkClient) {

    /**
     * Sjekk om uid i Sed finnes i PDL
     *
     * Konverterer 2 bokstavsutlandkode til trebokstavsutlandskode
     * Sjekker om 3-bokstavslandkode og identifikasjonsnummer fra Sed finnes i PDL
     *
     */
    fun finnesUidFraSedIPDL(
        utenlandskIdPDL: List<UtenlandskIdentifikasjonsnummer>,
        utenlandskIdSed: UtenlandskId
    ): Boolean {

        utenlandskIdPDL.forEach { utenlandskId ->
            val landkodeFraPdl = kodeverk.finnLandkode(utenlandskId.utstederland)
            if (utenlandskIdSed.land == landkodeFraPdl && utenlandskIdSed.id == utenlandskId.identifikasjonsnummer) {
                return true
            }
        }
        return false
    }
}

//    fun filtrerUidSomIkkeFinnesIPdl(identifisertPerson: IdentifisertPerson,
//                                    kodeverk: KodeverkClient,
//                                    institusjon: String) : IdentifisertPerson? {
//        //pdl pair (land, ident)
//        val pdlPair = identifisertPerson.uidFraPdl.map { Pair(it.utstederland, it.identifikasjonsnummer) }
//
//        //make new seduid validatet against pdluid (contrycode, ident) map use interface FinnLand (iso2->iso3) SE->SWE
//        val newSedUid = identifisertPerson.personIdenterFraSed.uid
//            .mapNotNull { seduid -> kodeverk.finnLandkode(seduid.utstederland)?.let {  UtenlandskPin(institusjon, seduid.identifikasjonsnummer, it) } }
//            .filterNot { seduid ->
//                //sed pair (land, ident)
//                val seduidPair = Pair( seduid.utstederland , seduid.identifikasjonsnummer)
//                //filter current seduidPair in all pdlPair
//                seduidPair in pdlPair
//            }
//        if (newSedUid.isEmpty()) return null //no new uid to add to pdl
//
//        val newpersonIdenterFraSed = identifisertPerson.personIdenterFraSed.copy(uid = newSedUid)
//        return identifisertPerson.copy(personIdenterFraSed = newpersonIdenterFraSed, uidFraPdl = emptyList()) //new ident with uid not in pdl
//    }

//    fun filtrerUidSomIkkeFinnesIPdl(identifisertPersoner: List<IdentifisertPerson>, kodeverk: KodeverkClient, institusjon: String): List<IdentifisertPerson> {
//        return  identifisertPersoner.mapNotNull { identifisertPerson -> filtrerUidSomIkkeFinnesIPdl(identifisertPerson, kodeverk, institusjon) }
//    }
//
//    fun finnesUidIPdl(uidFraPdl: List<UtenlandskIdentifikasjonsnummer>, uidFraSed: String): Boolean {
//        return uidFraPdl.any { it.identifikasjonsnummer == uidFraSed }
//    }
//}