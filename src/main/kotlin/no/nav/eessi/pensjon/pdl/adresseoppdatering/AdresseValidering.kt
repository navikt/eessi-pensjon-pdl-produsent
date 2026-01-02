package no.nav.eessi.pensjon.pdl.adresseoppdatering

import no.nav.eessi.pensjon.personoppslag.pdl.model.Metadata
import org.slf4j.LoggerFactory

object AdresseValidering {

    private val logger = LoggerFactory.getLogger(AdresseValidering::class.java)

    val maaInneholdeMinstEnBokstav = Regex(".*\\p{L}+.*")
    val maaInneholdeMinstEnBokstavEllerEtTall = Regex(".*[\\p{L}0-9]+.*")
    val postboksFraser = listOf("postboks", "postb", "postbox", "p.b", "po.box", "p.o.box").map { toCaseInsensitiveWordRegex(it) }
    val ukjentFraser = listOf("ukjent", "vet ikke", "unknown", "not known").map { toCaseInsensitiveWordRegex(it) }
    val ugyldigeTegn = listOf("\"", "\t")

    fun erGyldigAdressenavnNummerEllerBygningEtg(tekst: String) =
        tekst.matches(maaInneholdeMinstEnBokstav) &&
            !inneholderPostboksFraser(tekst) &&
            !inneholderUkjentFraser(tekst) &&
            !inneholderTegnPDLIkkeGodtar(tekst)

    fun erGyldigByStedEllerRegion(tekst: String) =
        tekst.matches(maaInneholdeMinstEnBokstav) &&
            !inneholderUkjentFraser(tekst) &&
            !inneholderTegnPDLIkkeGodtar(tekst)

    fun erGyldigPostKode(tekst: String) =
        tekst.matches(maaInneholdeMinstEnBokstavEllerEtTall) &&
            !inneholderUkjentFraser(tekst) &&
            !inneholderTegnPDLIkkeGodtar(tekst)

    /**
     * Fra PDL: Norsk bostedsadresse vil alltid ha Freg som master. Utenlandsk bostedsadresse vil alltid ha PDL som master.
     */
    fun erNorskAdresse(meta: Metadata?) : Boolean{
        return meta?.master.equals("FREG", ignoreCase = true).not().also { logger.info("erNorskAdresse: " +it) }
    }
    private fun inneholderPostboksFraser(tekst: String) = postboksFraser.any { tekst.contains(it) }
    private fun inneholderUkjentFraser(tekst: String) = ukjentFraser.any { tekst.contains(it) }
    private fun inneholderTegnPDLIkkeGodtar(tekst: String) = ugyldigeTegn.any { tekst.contains(it) }

    private fun toCaseInsensitiveWordRegex(tekst: String) =
        tekst
            .replace(".", "\\.")
            .let { Regex("""(\b|\W)$it(\b|\W)""", RegexOption.IGNORE_CASE)}

}