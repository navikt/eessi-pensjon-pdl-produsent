package no.nav.eessi.pensjon.pdl.validering

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
}