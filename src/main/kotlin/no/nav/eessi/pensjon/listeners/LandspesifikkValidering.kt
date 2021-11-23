package no.nav.eessi.pensjon.listeners

import org.slf4j.LoggerFactory

class LandspesifikkValidering() {

    private val logger = LoggerFactory.getLogger(LandspesifikkValidering::class.java)


    fun validerLandsspesifikkUID(landkode: String, uid: String): Boolean {
        logger.debug("valider land: $landkode, uid: $uid")
        return when (landkode) {
            "BEL" -> uid.length == 13 && uid.replace("-","") .length != 11 || uid.substring(6, 7) != "-" || uid.substring(8, 9) != "-"
            "BGR" -> uid.length != 10
            "FIN" -> uid.length != 11 || uid.substring(6, 7) != "-" || uid.substring(8, 9) != "-"
            "ISL" -> uid.length != 11 || uid.substring(6, 7) != "-" || uid.substring(8, 9) != "-"
            "DNK" -> uid.length != 11 || uid.substring(6, 7) != "-" || uid.substring(8, 9) != "-"
            else ->  false
        }.also { logger.debug("$landkode -> result  of our dreams: $it") }
    }


}