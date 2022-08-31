package no.nav.eessi.pensjon.pdl.validering

class AdresseValidering {

    val ugyldigPostInfo = listOf("postboks", "postb", "postbox", "p.b", "po.box", "ukjent", "vet ikke", "nn")
    val ugyldigFeltInfo = listOf("ukjent", "vet ikke", "nn")

    fun erGyldigAdressenavnNummerEllerBygningEtg(gate: String): Boolean {
        if (ugyldigPostInfo.any { gate.contains(it) }) return false
        if (!gate.matches(Regex("^(?=.*[\\p{L}])([0-9\\s\\p{L}]+)\$"))) return false
        return true
    }

    fun erGyldigByStedEllerRegion(byStedRegion: String): Boolean {
        if (ugyldigPostInformasjon(byStedRegion)) return false
        if (!byStedRegion.matches(Regex("^(?=.*[\\p{L}])([0-9\\s\\p{L}]+)\$"))) return false
        return true
    }

    fun erGyldigPostKode(postKode: String): Boolean {
        if (ugyldigPostInformasjon(postKode)) return false
        if (!postKode.matches(Regex("^([a-zA-Z0-9]+)(.*)\$"))) return false
        return true
    }

    private fun ugyldigPostInformasjon(byStedRegion: String) = ugyldigFeltInfo.any { byStedRegion.contains(it) }

}