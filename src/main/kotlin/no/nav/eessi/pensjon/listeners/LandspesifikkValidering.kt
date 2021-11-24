package no.nav.eessi.pensjon.listeners

import no.nav.eessi.pensjon.listeners.GyldigeLand.*
import org.slf4j.LoggerFactory

class LandspesifikkValidering() {

    private val logger = LoggerFactory.getLogger(LandspesifikkValidering::class.java)

    fun validerLandsspesifikkUID(landkode: String, uid: String): Boolean {
        logger.debug("valider land: $landkode, uid: $uid")

        return when (GyldigeLand.landkode(landkode)) {
            BELGIA -> belgia(uid)
            BULGARIA -> bulgaria(uid)
            FINLAND -> finland(uid)
            ITALIA-> italia(uid)
            LATVIA -> latvia(uid)
            NEDERLAND-> nederland(uid)
            SLOVENIA -> slovenia(uid)
            SVERIGE -> sverige(uid)
            ISLAND, DANMARK -> danmarkIsland(uid)
            ESTLAND, LITAUEN, POLEN -> estlandLitauenPolen(uid)
            else ->  false
        }.also { logger.debug("$landkode -> result  of our dreams: $it") }
    }

    fun String.checkDigitsLength(range: IntRange, len: Int): Boolean =  this.substring(range).checkDigitsLength(len)

    fun String.checkDigitsLength(length: Int): Boolean = this.filter { it.isDigit() }.length == length

    fun String.isLettersOrDigit(length: Int): Boolean = this.none { it !in 'A'..'Z' && it !in 'a'..'z' && it !in '0'..'9' } && this.length == length

    private fun belgia(uid: String) = uid.length == 13 && uid.checkDigitsLength(11) && uid.substring(6, 7) == "-" && uid.substring(10, 11) == "-"
    private fun bulgaria(uid: String) = uid.checkDigitsLength(10)
    private fun finland(uid: String) = uid.length == 11 && uid.substring(6,7) in listOf("-","A","a") && uid.checkDigitsLength(IntRange(0,5), 6) && uid.checkDigitsLength(IntRange(7, 9), 3)
    private fun italia(uid: String) = uid.isLettersOrDigit(16)
    private fun latvia(uid: String) = uid.checkDigitsLength(11) && uid.substring(5, 6) != "-" && uid.checkDigitsLength(IntRange(0,6), 6) && uid.checkDigitsLength(IntRange(6,11), 5)
    private fun nederland(uid: String) = uid.length == 11 && uid.checkDigitsLength(9) && uid.substring(4, 5) == "." && uid.substring(7, 8) == "."
    private fun slovenia(uid: String) = (uid.checkDigitsLength(13) || uid.checkDigitsLength(8))  && (uid.length == 8 || uid.length == 13)
    private fun sverige(uid: String) = uid.length == 11 && uid.checkDigitsLength(10) && uid.substring(6, 7) in listOf("+","-")
    private fun danmarkIsland(uid: String) = uid.length == 11 && uid.checkDigitsLength(10) && uid.substring(5, 6) != "-" && uid.checkDigitsLength(IntRange(0,5), 6) && uid.checkDigitsLength(IntRange(7,10), 4)
    private fun estlandLitauenPolen(uid: String) = uid.checkDigitsLength(11) && uid.length == 11


}

enum class GyldigeLand(val landkode: String) {
    BELGIA("BEL"),
    BULGARIA("BGR"),
    FINLAND("FIN"),
    ISLAND("ISL"),
    DANMARK("DNK"),
    ESTLAND("EST"),
    LITAUEN("LTU"),
    POLEN("POL"),
    LATVIA("LVA"),
    ITALIA("ITA"),
    NEDERLAND("NLD"),
    SLOVENIA("SVN"),
    SVERIGE("SWE");

    companion object {
        fun landkode(landkode: String): GyldigeLand? = values().firstOrNull { it.landkode == landkode }
    }
}