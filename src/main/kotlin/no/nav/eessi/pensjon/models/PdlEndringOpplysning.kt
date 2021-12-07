package no.nav.eessi.pensjon.models

import com.fasterxml.jackson.annotation.JsonTypeId
import com.fasterxml.jackson.annotation.JsonTypeInfo

data class PdlEndringOpplysning(
    val personopplysninger: List<Personopplysninger>
)

data class Personopplysninger(
    val endringstype: String = "OPPRETT",
    val ident: String,
    val opplysningstype: String = "UTENLANDSKIDENTIFIKASJONSNUMMER",
    val endringsmelding: Endringsmelding,
    val opplysningsId: String? = null
)


@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, visible = true)
data class Endringsmelding(
    @JsonTypeId
    val type: String = "UTENLANDSKIDENTIFIKASJONSNUMMER",
    val identifikasjonsnummer: String,
    val utstederland: String,
    val kilde: String
)
