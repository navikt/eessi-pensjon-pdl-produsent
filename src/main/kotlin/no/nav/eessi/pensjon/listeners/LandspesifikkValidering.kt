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
            "ISL", "DNK" -> uid.length == 11 && uid.checkDigitsLength(10) && uid.substring(5, 6) != "-" && uid.checkDigitsLength(IntRange(0,5), 6) && uid.checkDigitsLength(IntRange(7,10), 4)
            "EST", "LTU", "POL" -> uid.checkDigitsLength(11) && uid.length == 11
            "ITA" -> uid.isLettersOrDigit(16)
            "LVA" -> uid.checkDigitsLength(11) && uid.substring(5, 6) != "-" && uid.checkDigitsLength(IntRange(0,6), 6) && uid.checkDigitsLength(IntRange(6,11), 5)
            "NLD" -> uid.length == 11 && uid.checkDigitsLength(9) && uid.substring(4, 5) == "." && uid.substring(7, 8) == "."
            "SLO" -> uid.checkDigitsLength(13) || uid.checkDigitsLength(8)
            else ->  false
        }.also { logger.debug("$landkode -> result  of our dreams: $it") }
    }

    fun String.checkDigitsLength(range: IntRange, len: Int): Boolean =  this.substring(range).checkDigitsLength(len)

    fun String.checkDigitsLength(length: Int): Boolean = this.filter { it.isDigit() }.length == length

    fun String.isLettersOrDigit(length: Int): Boolean {
        return this.none { it !in 'A'..'Z' && it !in 'a'..'z' && it !in '0'..'9' } && this.length == length
    }

}