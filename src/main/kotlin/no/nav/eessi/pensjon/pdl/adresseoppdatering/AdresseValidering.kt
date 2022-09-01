package no.nav.eessi.pensjon.pdl.adresseoppdatering

class AdresseValidering {

    val maaInneholdeMinstEnBokstav = Regex(".*\\p{L}+.*")
    val maaInneholdeMinstEnBokstavEllerEtTall = Regex(".*[\\p{L}0-9]+.*")
    val ugyldigPostInfo = listOf("postboks", "postb", "postbox", "p.b", "po.box")
    val ugyldigFeltInfo = listOf("ukjent", "vet ikke", "nn")

    fun erGyldigAdressenavnNummerEllerBygningEtg(tekst: String): Boolean {
        if (ugyldigPostInfo.any { tekst.contains(it) }) return false
        if (ugyldigFeltInfo.any { tekst.contains(it) }) return false
        if (!tekst.matches(maaInneholdeMinstEnBokstav)) return false
        return true
    }

    fun erGyldigByStedEllerRegion(tekst: String): Boolean {
        if (ugyldigFeltInfo.any { tekst.contains(it) }) return false
        if (!tekst.matches(maaInneholdeMinstEnBokstav)) return false
        return true
    }

    fun erGyldigPostKode(tekst: String): Boolean {
        if (ugyldigFeltInfo.any { tekst.contains(it) }) return false
        if (!tekst.matches(maaInneholdeMinstEnBokstavEllerEtTall)) return false
        return true
    }

}