package no.nav.eessi.pensjon.pdl.validering

import no.nav.eessi.pensjon.listeners.LandspesifikkValidering
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson

class PdlValidering {

    fun finnesIdentifisertePersoner(
        identifisertPersoner:List<IdentifisertPerson>,
    ): Boolean {
        if (identifisertPersoner.isEmpty()) {
            return false
        }
        return true
    }

    fun validerUid(identifisertPersoner: List<IdentifisertPerson>): List<IdentifisertPerson> {
        val validering = LandspesifikkValidering()
        val gyldigepersoner = identifisertPersoner.filter { it.personIdenterFraSed.uid.size == 1 }
        return gyldigepersoner.filter { ident ->
            val uid = ident.personIdenterFraSed.uid.first()
            validering.validerLandsspesifikkUID(uid.utstederland, uid.identifikasjonsnummer)
        }
    }

    fun finnesAvsenderInstitusjon(avsenderNavn: String?) = avsenderNavn != null
}