package no.nav.eessi.pensjon.models

import com.fasterxml.jackson.annotation.JsonTypeId
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endringstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Opplysningstype

data class PdlEndringOpplysning(
    val personopplysninger: List<Personopplysninger>
)

data class Personopplysninger(
    val endringstype: Endringstype,
    val ident: String,
    val opplysningstype: Opplysningstype,
    val endringsmelding: EndringsmeldingUID,
    val opplysningsId: String? = null
)

/*abstract class Endringsmelding{
    abstract val kilde: String
}*/
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, visible = true)
data class EndringsmeldingUID(
    @JsonTypeId
    val type: String = Opplysningstype.UTENLANDSKIDENTIFIKASJONSNUMMER.name,
    val kilde: String = "",
    val identifikasjonsnummer: String,
    val utstederland: String,
)

/*@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, visible = true)
data class EndringsmeldingUtAdresse(
    @JsonTypeId
    val type: String = Opplysningstype.KONTAKTADRESSE.name,
    override val kilde: String = "",
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val gyldigFraOgMed: LocalDate?,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val gylidgTilOgMed: LocalDate?,
    val coAdressenavn: String?,
    val adresse: UtenlandskAdresse?
)*/

