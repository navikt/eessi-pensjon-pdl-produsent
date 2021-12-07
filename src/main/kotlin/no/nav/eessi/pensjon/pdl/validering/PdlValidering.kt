package no.nav.eessi.pensjon.pdl.validering

import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import org.slf4j.LoggerFactory

class PdlValidering {
    private val logger = LoggerFactory.getLogger(PdlValidering::class.java)

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
    fun personerValidertPaaLand(identifisertPersoner: List<IdentifisertPerson>): List<IdentifisertPerson> {
        val validering = LandspesifikkValidering()

        //TODO Lag oppgave når det er flere
        val gyldigepersoner = identifisertPersoner.filter { it.personIdenterFraSed.uid.size == 1 }

        return gyldigepersoner.filter { ident ->
            val uid = ident.personIdenterFraSed.uid.first()
            validering.validerLandsspesifikkUID(uid.utstederland, uid.identifikasjonsnummer)
        }.also { logger.info("Det er funnet ${it.size} gyldige personer validert på land") }
    }

    fun erUidLandAnnetEnnAvsenderLand(
        identifisertPersoner: List<IdentifisertPerson>,
        avsenderLand: String
    ): Boolean {
        val alleUidLand =  identifisertPersoner.flatMap { it -> it.personIdenterFraSed.uid }.map { it.utstederland }.distinct()
        if (alleUidLand.any { it != avsenderLand }) {
            logger.info("Avsender land er et annet enn UID land")
            return true
        }
        return false
    }

}