package no.nav.eessi.pensjon.pdl.adresseoppdatering

class AdresseValidering {

    val maaInneholdeMinstEnBokstav = Regex(".*\\p{L}+.*")
    val maaInneholdeMinstEnBokstavEllerEtTall = Regex(".*[\\p{L}0-9]+.*")
    val postboksFraser = listOf("postboks", "postb", "postbox", "p.b", "po.box")
    val ukjentFraser = listOf("ukjent", "vet ikke", "nn")

    fun erGyldigAdressenavnNummerEllerBygningEtg(tekst: String): Boolean {
        if (!tekst.matches(maaInneholdeMinstEnBokstav)) return false
        if (postboksFraser.any { tekst.contains(it) }) return false
        if (ukjentFraser.any { tekst.contains(it) }) return false
        return true
    }

    fun erGyldigByStedEllerRegion(tekst: String): Boolean {
        if (!tekst.matches(maaInneholdeMinstEnBokstav)) return false
        if (ukjentFraser.any { tekst.contains(it) }) return false
        return true
    }

    fun erGyldigPostKode(tekst: String): Boolean {
        if (!tekst.matches(maaInneholdeMinstEnBokstavEllerEtTall)) return false
        if (ukjentFraser.any { tekst.contains(it) }) return false
        return true
    }

}