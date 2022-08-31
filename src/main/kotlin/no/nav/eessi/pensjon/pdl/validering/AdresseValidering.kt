package no.nav.eessi.pensjon.pdl.validering

class AdresseValidering {

    val postBoksInfo = listOf("postboks", "postb", "postbox", "p.b", "po.box")

    val ugyldigPostInfo = listOf("postboks", "postb", "postbox", "p.b", "po.box", "ukjent", "vet ikke", "nn")
    val ugyldigFeltInfo = listOf("ukjent", "vet ikke", "nn")

    fun erGyldigAdressenavnNummerEllerBygningEtg(gate: String): Boolean {
        if (ugyldigPostInfo.any { gate.contains(it) }) {
            return false
        }
        if (!gate.matches(Regex("[a-zA-Z]+"))) {
            return false
        }
        return true
    }

    fun erGyldigByStedEllerRegion(byStedRegion: String): Boolean {
        if (ugyldigFeltInfo.any { byStedRegion.contains(it) }) {
            return false
        }
        if (!byStedRegion.matches(Regex("^(?=.*[a-zA-Z\\u0080-\\u024F])([a-zA-Z0-9\\s\\u0080-\\u024F]+)\$"))) { //med utvidet latinsk alfabet
            return false
        }
        return true
    }

    fun erGyldigPostKode(postKode: String): Boolean {
        if (ugyldigFeltInfo.any { postKode.contains(it) }) {
            return false
        }
        if (!postKode.matches(Regex("^([a-zA-Z0-9]+)(.*)\$"))) {
            return false
        }
        return true
    }

    fun inneholderPostBoksInfo(gate: String): Boolean {
        if (postBoksInfo.any { gate.contains(it) }) {
            return true
        }
        return false
    }
}