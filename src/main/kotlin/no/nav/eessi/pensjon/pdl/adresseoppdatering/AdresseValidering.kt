package no.nav.eessi.pensjon.pdl.adresseoppdatering

class AdresseValidering {

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
        if (!byStedRegion.matches(Regex("^(?=.*[a-zA-Z])([a-zA-Z0-9]+)\$"))) {
            return false
        }
        return true
    }

    fun erGyldigPostKode(postKode: String): Boolean {
        if (ugyldigFeltInfo.any { postKode.contains(it) }) {
            return false
        }
        if (!postKode.matches(Regex("[a-zA-Z0-9]+"))) {
            return false
        }
        return true
    }
}