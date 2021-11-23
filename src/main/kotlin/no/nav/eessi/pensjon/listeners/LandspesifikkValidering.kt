package no.nav.eessi.pensjon.listeners

class LandspesifikkValidering() {

    fun validerLandsspesifikkUID(landkode: String, uid: String): Boolean {
        return when (landkode) {
            "BEL" -> uid.length != 11 || uid.substring(6, 7) != "-" || uid.substring(8, 9) != "-"
            "BGR" -> uid.length != 11 || uid.substring(6, 7) != "-" || uid.substring(8, 9) != "-"
            "FIN" -> uid.length != 11 || uid.substring(6, 7) != "-" || uid.substring(8, 9) != "-"
            "ISL" -> uid.length != 11 || uid.substring(6, 7) != "-" || uid.substring(8, 9) != "-"
            "DNK" -> uid.length != 11 || uid.substring(6, 7) != "-" || uid.substring(8, 9) != "-"
            else ->  false
        }
    }

}