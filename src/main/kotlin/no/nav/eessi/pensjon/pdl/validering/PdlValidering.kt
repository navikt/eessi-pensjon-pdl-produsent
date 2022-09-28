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
    fun erPersonValidertPaaLand(utenlandskId: String, land: String) =
        validering.validerLandsspesifikkUID(kodeverkClient.finnLandkode(land)!!, utenlandskId)

    fun avsenderLandHarVerdiOgErSammeSomUidLand(utenlandskId: UtenlandskId, avsenderLand: String?) =
        if (!avsenderLand.isNullOrEmpty() && utenlandskId.land == avsenderLand) {
            true
        } else {
            logger.info("Avsender land er et annet enn UID land")
            false
        }

}
