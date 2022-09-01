package no.nav.eessi.pensjon.pdl.adresseoppdatering

class AdresseValidering {

    val maaInneholdeMinstEnBokstav = Regex(".*\\p{L}+.*")
    val maaInneholdeMinstEnBokstavEllerEtTall = Regex(".*[\\p{L}0-9]+.*")
    val postboksFraser = listOf("postboks", "postb", "postbox", "p.b", "po.box")  // Bruk lowercase fraser
    val ukjentFraser = listOf("ukjent", "vet ikke", "nn")  // Bruk lowercase fraser

    fun erGyldigAdressenavnNummerEllerBygningEtg(tekst: String) =
        tekst.matches(maaInneholdeMinstEnBokstav) && !inneholderPostboksFraser(tekst) && !inneholderUkjentFraser(tekst)

    fun erGyldigByStedEllerRegion(tekst: String) =
        tekst.matches(maaInneholdeMinstEnBokstav) && !inneholderUkjentFraser(tekst)

    fun erGyldigPostKode(tekst: String) =
        tekst.matches(maaInneholdeMinstEnBokstavEllerEtTall) && !inneholderUkjentFraser(tekst)

    private fun inneholderPostboksFraser(tekst: String) = postboksFraser.any { tekst.lowercase().contains(it) }
    private fun inneholderUkjentFraser(tekst: String) = ukjentFraser.any { tekst.lowercase().contains(it) }

}