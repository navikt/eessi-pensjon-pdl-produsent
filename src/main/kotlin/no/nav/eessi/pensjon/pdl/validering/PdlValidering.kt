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

    /**
     * Kjører landspesifik validering
     * Aksepterer kun et nytt UID om gangen
     */
    fun validerUid(identifisertPersoner: List<IdentifisertPerson>): List<IdentifisertPerson> {
        val validering = LandspesifikkValidering()

        //TODO Lag oppgave når det er flere
        val gyldigepersoner = identifisertPersoner.filter { it.personIdenterFraSed.uid.size == 1 }

        return gyldigepersoner.filter { ident ->
            val uid = ident.personIdenterFraSed.uid.first()
            validering.validerLandsspesifikkUID(uid.utstederland, uid.identifikasjonsnummer)
        }
    }

    fun erUidLandAnnetEnnAvsenderLand(
        identifisertPersoner: List<IdentifisertPerson>,
        avsenderLand: String
    ): Boolean {
        val alleUidLand =
            identifisertPersoner.flatMap { it -> it.personIdenterFraSed.uid }.map { it.utstederland }.distinct()
        if (alleUidLand.any { it != avsenderLand }) {
            return true
        }
        return false
    }

    fun finnesAvsenderInstitusjon(avsenderNavn: String?) = avsenderNavn != null
}