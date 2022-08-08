package no.nav.eessi.pensjon.models

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonTypeId
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endringstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Opplysningstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskAdresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.Vegadresse
import java.time.LocalDate
import java.time.LocalDateTime

data class PdlEndringOpplysning(
    val personopplysninger: List<Personopplysninger>
)

data class Personopplysninger(
    val endringstype: Endringstype,
    val ident: String,
    val opplysningstype: Opplysningstype,
    val endringsmelding: Endringsmelding,
    val opplysningsId: String? = null
)

interface Endringsmelding{
    val type: String
    val kilde: String
}
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, visible = true)
data class EndringsmeldingUID(
    @JsonTypeId
    override val type: String = Opplysningstype.UTENLANDSKIDENTIFIKASJONSNUMMER.name,
    override val kilde: String = "",
    val identifikasjonsnummer: String,
    val utstederland: String,
): Endringsmelding

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, visible = true)
data class EndringsmeldingUtAdresse(
    @JsonTypeId
    override val type: String = Opplysningstype.KONTAKTADRESSE.name,
    override val kilde: String = "",
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val gyldigFraOgMed: LocalDateTime?,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val gylidgTilOgMed: LocalDateTime?,
    val coAdressenavn: String?,
    val adresse: UtenlandskAdresse?
): Endringsmelding

