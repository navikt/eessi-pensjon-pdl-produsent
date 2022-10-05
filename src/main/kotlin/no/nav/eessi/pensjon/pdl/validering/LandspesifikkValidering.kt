package no.nav.eessi.pensjon.pdl.validering

import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.pdl.validering.GyldigeLand.BELGIA
import no.nav.eessi.pensjon.pdl.validering.GyldigeLand.BULGARIA
import no.nav.eessi.pensjon.pdl.validering.GyldigeLand.DANMARK
import no.nav.eessi.pensjon.pdl.validering.GyldigeLand.ESTLAND
import no.nav.eessi.pensjon.pdl.validering.GyldigeLand.FINLAND
import no.nav.eessi.pensjon.pdl.validering.GyldigeLand.FRANKRIKE
import no.nav.eessi.pensjon.pdl.validering.GyldigeLand.ISLAND
import no.nav.eessi.pensjon.pdl.validering.GyldigeLand.ITALIA
import no.nav.eessi.pensjon.pdl.validering.GyldigeLand.LATVIA
import no.nav.eessi.pensjon.pdl.validering.GyldigeLand.LITAUEN
import no.nav.eessi.pensjon.pdl.validering.GyldigeLand.NEDERLAND
import no.nav.eessi.pensjon.pdl.validering.GyldigeLand.POLEN
import no.nav.eessi.pensjon.pdl.validering.GyldigeLand.SLOVENIA
import no.nav.eessi.pensjon.pdl.validering.GyldigeLand.SPANIA
import no.nav.eessi.pensjon.pdl.validering.GyldigeLand.STORBRITANNIA
import no.nav.eessi.pensjon.pdl.validering.GyldigeLand.SVERIGE
import no.nav.eessi.pensjon.pdl.validering.GyldigeLand.TYSKLAND
import no.nav.eessi.pensjon.pdl.validering.GyldigeLand.UNGARN
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class LandspesifikkValidering(private val kodeverkClient: KodeverkClient) {

    private val logger = LoggerFactory.getLogger(LandspesifikkValidering::class.java)

    fun validerLandsspesifikkUID(landkode: String, uid: String): Boolean {
        logger.debug("valider land: $landkode, uid: $uid, len: ${uid.length}")

        val land = kodeverkClient.finnLandkode(landkode)
        if(land.isNullOrEmpty()) return false

        return when (GyldigeLand.landkode(land)) {
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
            TYSKLAND -> tyskland(uid)
            UNGARN -> ungarn(uid)
            FRANKRIKE -> frankrike(uid)
            SPANIA -> spania(uid)
            STORBRITANNIA -> storbritannia(uid)
            else ->  false
        }.also { logger.debug("$landkode -> result  of our dreams: $it") }
    }

    /**
     * Vi aksepterer feil i uid for noen land, gitt at det er mulig å konvertere til et kjent format
     */
    fun normalisertPin(uid: String, land: String): String {
        return if (GyldigeLand.landkode(land) == SVERIGE) {
            formaterSvenskUID(uid)
        }
        else uid
    }

    fun String.checkDigitsLength(range: IntRange, len: Int): Boolean =  this.substring(range).checkDigitsLength(len)

    fun String.checkDigitsLength(length: Int): Boolean = this.filter { it.isDigit() }.length == length

    fun String.isLettersOrDigit(): Boolean = this.none { it !in 'A'..'Z' && it !in 'a'..'z' && it !in '0'..'9' }

    private fun erHvertredjeBokstavBlank(str: String): Boolean {
        for(i in 2 until str.length step 3) {
            if (str[i] != ' ') return false
        }
        return true
    }

    private fun belgia(uid: String) = uid.length == 13 && uid.checkDigitsLength(11) && uid.substring(6, 7) == "-" && uid.substring(10, 11) == "-"
    private fun bulgaria(uid: String) = uid.checkDigitsLength(10)
    private fun finland(uid: String) = uid.length == 11 && uid.substring(6,7) in listOf("-","A","a") && uid.checkDigitsLength(IntRange(0,5), 6) && uid.checkDigitsLength(IntRange(7, 9), 3)
    private fun italia(uid: String) = uid.isLettersOrDigit() && uid.length == 16
    private fun latvia(uid: String) = uid.checkDigitsLength(11) && uid.substring(5, 6) != "-" && uid.checkDigitsLength(IntRange(0,6), 6) && uid.checkDigitsLength(IntRange(6,11), 5)
    private fun nederland(uid: String) = uid.length == 11 && uid.checkDigitsLength(9) && uid.substring(4, 5) == "." && uid.substring(7, 8) == "."
    private fun slovenia(uid: String) = (uid.checkDigitsLength(13) || uid.checkDigitsLength(8))  && (uid.length == 8 || uid.length == 13)

    /**
     * Godkjente Svenske UIDer kan forekomme i følgende format:
     * - som 12 siffer pluss "-" eller "+"
     * - som 10 siffer pluss "-" eller "+"
     * - som 12 siffer
     * - som 10 siffer
     * Disse reglene gjelder ikke for PDL, men kommer av erfaring fra saksbehandler og godkjent fra Produkteier.
     */
    private fun sverige(uid: String): Boolean {
        return formaterSvenskUID(uid).length == 11 && formaterSvenskUID(uid).checkDigitsLength(10)
    }

     fun formaterSvenskUID(uid: String): String {
        var uidNew = uid.trim().replace(" ", "").replace("-", "")
        if (uidNew.length == 12) {
            uidNew = uidNew.removeRange(0, 2)
        }
        return uidNew.replaceRange(6, 6, "-")
    }

    private fun danmarkIsland(uid: String) = uid.length == 11 && uid.checkDigitsLength(10) && uid.substring(6, 7) == "-" && uid.checkDigitsLength(IntRange(0,5), 6) && uid.checkDigitsLength(IntRange(7,10), 4)
    private fun estlandLitauenPolen(uid: String) = uid.checkDigitsLength(11) && uid.length == 11
    private fun tyskland(uid: String): Boolean {
        return uid.length == 15 && uid.substring(2,3) == " " && uid.substring(9, 10) == " " && uid.substring(11,12) == " " &&
                uid.checkDigitsLength(IntRange(0,2), 2) &&
                uid.checkDigitsLength(IntRange(3, 8), 6) &&
                uid.checkDigitsLength(IntRange(12, 14), 3)
    }
    private fun ungarn(uid: String): Boolean = uid.checkDigitsLength(9) && uid.length == 11 && uid.substring(3,4) == "-" && uid.substring(7,8) == "-"
    private fun frankrike(uid: String): Boolean = uid.checkDigitsLength(13) && uid.length == 18  && uid.substring(1,2) == " " && uid.substring(4,5) == " " && uid.substring(7,8) == " " && uid.substring(10,11) == " "  && uid.substring(14,15) == " "
    private fun spania(uid: String): Boolean = uid.isLettersOrDigit() && uid.length == 10
    private fun storbritannia(uid: String): Boolean = uid.replace(" ","") .isLettersOrDigit() && erHvertredjeBokstavBlank(uid)  && uid.length > 10

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
    SVERIGE("SWE"),
    SPANIA("ESP"),
    TYSKLAND("DEU"),
    UNGARN("HUN"),
    FRANKRIKE("FRA"),
    STORBRITANNIA("GBR");

    companion object {
        fun landkode(landkode: String): GyldigeLand? = values().firstOrNull { it.landkode == landkode }
    }
}