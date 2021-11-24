package no.nav.eessi.pensjon.listeners

import no.nav.eessi.pensjon.listeners.GyldigeLand.BELGIA
import no.nav.eessi.pensjon.listeners.GyldigeLand.BULGARIA
import no.nav.eessi.pensjon.listeners.GyldigeLand.DANMARK
import no.nav.eessi.pensjon.listeners.GyldigeLand.ESTLAND
import no.nav.eessi.pensjon.listeners.GyldigeLand.FINLAND
import no.nav.eessi.pensjon.listeners.GyldigeLand.ISLAND
import no.nav.eessi.pensjon.listeners.GyldigeLand.ITALIA
import no.nav.eessi.pensjon.listeners.GyldigeLand.LATVIA
import no.nav.eessi.pensjon.listeners.GyldigeLand.LITAUEN
import no.nav.eessi.pensjon.listeners.GyldigeLand.NEDERLAND
import no.nav.eessi.pensjon.listeners.GyldigeLand.POLEN
import no.nav.eessi.pensjon.listeners.GyldigeLand.SLOVENIA
import org.slf4j.LoggerFactory

class LandspesifikkValidering() {

    private val logger = LoggerFactory.getLogger(LandspesifikkValidering::class.java)

    fun validerLandsspesifikkUID(landkode: String, uid: String): Boolean {
        logger.debug("valider land: $landkode, uid: $uid")
        return when (GyldigeLand.landkode(landkode)) {
            BELGIA -> uid.length == 13 && uid.checkDigitsLength(11) && uid.substring(6, 7) == "-" && uid.substring(10, 11) == "-"
            BULGARIA -> uid.length == 10 && uid.checkDigitsLength(10)
            FINLAND -> uid.length == 11 && uid.substring(6,7) in listOf("-","A","a") && uid.checkDigitsLength(IntRange(0,5), 6) && uid.checkDigitsLength(IntRange(7, 9), 3)
            ISLAND, DANMARK -> uid.length == 11 && uid.checkDigitsLength(10) && uid.substring(5, 6) != "-" && uid.checkDigitsLength(IntRange(0,5), 6) && uid.checkDigitsLength(IntRange(7,10), 4)
            ESTLAND, LITAUEN, POLEN ->  uid.length == 11 && uid.checkDigitsLength(11)
            ITALIA -> uid.length == 16 && uid.isLettersOrDigit(16)
            LATVIA ->  uid.length == 12 && uid.checkDigitsLength(11) && uid.substring(5, 6) != "-" && uid.checkDigitsLength(IntRange(0,6), 6) && uid.checkDigitsLength(IntRange(6,11), 5)
            NEDERLAND -> uid.length == 11 && uid.checkDigitsLength(9) && uid.substring(4, 5) == "." && uid.substring(7, 8) == "."
            SLOVENIA -> uid.checkDigitsLength(13) || uid.checkDigitsLength(8)
            else ->  false
        }.also { logger.debug("$landkode -> result  of our dreams: $it") }
    }

    fun String.checkDigitsLength(range: IntRange, len: Int): Boolean =  this.substring(range).checkDigitsLength(len)

    fun String.checkDigitsLength(length: Int): Boolean = this.filter { it.isDigit() }.length == length

    fun String.isLettersOrDigit(length: Int): Boolean = this.none { it !in 'A'..'Z' && it !in 'a'..'z' && it !in '0'..'9' } && this.length == length

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