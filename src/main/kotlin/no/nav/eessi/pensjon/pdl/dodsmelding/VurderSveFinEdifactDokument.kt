package no.nav.eessi.pensjon.pdl.dodsmelding

import no.nav.eessi.pensjon.utils.toJson

class VurderSveFinEdifactDokument {

    fun vurderEditfactDokument(edifactDokument: String?): EdifactDokument? {
        if (edifactDokument.isNullOrBlank()) return null

        val segments = normaliser(edifactDokument)
            .split("'")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (segments.isEmpty()) return null

        val unb = finnSegment(segments, "UNB")
        val bgm = finnSegment(segments, "BGM")
        val frNad = finnNadForRole(segments, "FR")
        val mrNad = finnNadForRole(segments, "MR")
        val dtm329 = finnDtmForQualifier(segments, "329")

        val avsenderLand = hentSisteFelt(frNad)
        val mottakerLand = hentSisteFelt(mrNad)

        val sveFin = setOf("SE", "FI", "SWE", "FIN")

        return EdifactDokument(
            avsender = hentFelt(unb, 2),
            mottaker = hentFelt(unb, 3),
            meldingstype = hentFelt(bgm, 1),
            referanse = hentFelt(bgm, 2),
            avsenderLand = avsenderLand,
            mottakerLand = mottakerLand,
            fodselsdato = hentDatoFraDtm(dtm329),
            erSveFin = listOf(avsenderLand, mottakerLand).any { it in sveFin }
        ).also { println( "Tolkning av EDIFACT: ${it.toJson()}") }
    }

    private fun normaliser(edifact: String): String =
        edifact
            .replace('’', '\'')
            .replace('\n', ' ')
            .replace('\r', ' ')

    private fun finnSegment(segments: List<String>, navn: String): String? =
        segments.firstOrNull { it.startsWith("$navn+") }

    private fun finnNadForRole(segments: List<String>, role: String): String? =
        segments.firstOrNull { it.startsWith("NAD+$role+") }

    private fun finnDtmForQualifier(segments: List<String>, qualifier: String): String? =
        segments.firstOrNull { it.startsWith("DTM+$qualifier:") }

    private fun hentFelt(segment: String?, index: Int): String? =
        segment?.split('+')?.getOrNull(index)?.takeIf { it.isNotBlank() }

    private fun hentSisteFelt(segment: String?): String? =
        segment
            ?.split('+')
            ?.lastOrNull()
            ?.takeIf { it.isNotBlank() }

    private fun hentDatoFraDtm(segment: String?): String? =
        segment
            ?.split('+')
            ?.getOrNull(1)
            ?.split(':')
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
}
