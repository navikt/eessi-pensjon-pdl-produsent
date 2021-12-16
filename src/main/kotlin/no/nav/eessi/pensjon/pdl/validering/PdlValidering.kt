package no.nav.eessi.pensjon.pdl.validering

import no.nav.eessi.pensjon.eux.UtenlandskId
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PdlValidering(private val kodeverkClient: KodeverkClient) {

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
    fun erPersonValidertPaaLand(utenlandskId: UtenlandskId): Boolean {
        val validering = LandspesifikkValidering()

        return validering.validerLandsspesifikkUID(kodeverkClient.finnLandkode(utenlandskId.land)!!, utenlandskId.id)
    }

    fun erUidLandAnnetEnnAvsenderLand(
        utenlandskId: UtenlandskId,
        avsenderLand: String
    ): Boolean {
        if (utenlandskId.land != avsenderLand) {
            logger.info("Avsender land er et annet enn UID land")
            return true
        }
        return false
    }

    /**
     * @return true dersom uid fra sed er lik uid fra pdl
     * Sjekker om uid fra sed er lik uid i pdl.
     */
    fun finnesAlleredeIPDL(alleUidIPDL: List<String>, uidISed: String) : Boolean {
        return  alleUidIPDL.any { it in uidISed }
    }
}
