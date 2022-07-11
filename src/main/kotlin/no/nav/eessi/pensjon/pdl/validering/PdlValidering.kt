package no.nav.eessi.pensjon.pdl.validering

import no.nav.eessi.pensjon.eux.UtenlandskId
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.klienter.kodeverk.KodeverkClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PdlValidering(private val kodeverkClient: KodeverkClient) {

    private val logger = LoggerFactory.getLogger(PdlValidering::class.java)
    private val validering = LandspesifikkValidering

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

    fun finnesUgyldigeSederIBuc(seder: List<SED>, antallsediBuc: Int) : Boolean {
        return seder.size == antallsediBuc

    }



}
