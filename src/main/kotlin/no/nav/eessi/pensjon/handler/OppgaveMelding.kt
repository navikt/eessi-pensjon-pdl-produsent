package no.nav.eessi.pensjon.handler

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.utils.toJson

@JsonIgnoreProperties(ignoreUnknown = true)
data class OppgaveMelding(
    val sedType: SedType?,
    val journalpostId: String? = null,
    val tildeltEnhetsnr: Enhet,
    val aktoerId: String?,
    val rinaSakId: String,
    val hendelseType: HendelseType,
    var filnavn: String?,
    val oppgaveType: OppgaveType
)  {

    override fun toString(): String {
        return toJson()
    }

}
enum class OppgaveType{
    PDL,
    BEHANDLE_SED,
    JOURNALFORING,
    KRAV; //støtter ikke tildeltenhet 9999
}
