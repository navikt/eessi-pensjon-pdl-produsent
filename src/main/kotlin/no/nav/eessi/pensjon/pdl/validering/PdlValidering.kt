package no.nav.eessi.pensjon.pdl.validering

import no.nav.eessi.pensjon.eux.UtenlandskId
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PdlValidering(private val kodeverkClient: KodeverkClient) {

    private val logger = LoggerFactory.getLogger(PdlValidering::class.java)
    private val validering = LandspesifikkValidering

    /**
     * Kjører landspesifik validering
     * Aksepterer kun et nytt UID om gangen
     */
    fun erPersonValidertPaaLand(utenlandskId: UtenlandskId) =
        validering.validerLandsspesifikkUID(kodeverkClient.finnLandkode(utenlandskId.land)!!, utenlandskId.id)

    fun erUidLandAnnetEnnAvsenderLand(utenlandskId: UtenlandskId, avsenderLand: String) =
        if (utenlandskId.land != avsenderLand) {
            logger.info("Avsender land er et annet enn UID land")
            true
        } else false

}
