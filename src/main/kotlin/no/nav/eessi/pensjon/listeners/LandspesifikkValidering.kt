package no.nav.eessi.pensjon.listeners

import org.slf4j.LoggerFactory

class LandspesifikkValidering() {

    private val logger = LoggerFactory.getLogger(LandspesifikkValidering::class.java)


    fun validerLandsspesifikkUID(landkode: String, uid: String): Boolean {
        logger.debug("valider land: $landkode, uid: $uid")
        return when (landkode) {
            "BEL" -> uid.length == 13 && uid.checkDigitsLength(11) && uid.substring(6, 7) == "-" && uid.substring(10, 11) == "-"
            "BGR" -> uid.checkDigitsLength(10)
            "FIN" -> uid.length == 11 && uid.substring(6,7) in listOf("-","A","a") && uid.checkDigitsLength(IntRange(0,5), 6) && uid.checkDigitsLength(IntRange(7, 9), 3)
            "ISL" -> uid.length != 11 || uid.substring(6, 7) != "-" || uid.substring(8, 9) != "-"
            "DNK" -> uid.replace("[-:./\\s]".toRegex(), "").also { logger.debug(it) } .length == 11
            else ->  false
        }.also { logger.debug("$landkode -> result  of our dreams: $it") }
    }

    fun String.checkDigitsLength(range: IntRange, len: Int): Boolean =  this.substring(range).checkDigitsLength(len)

    fun String.checkDigitsLength(len: Int): Boolean = this.filter { it.isDigit() }.length == len

}