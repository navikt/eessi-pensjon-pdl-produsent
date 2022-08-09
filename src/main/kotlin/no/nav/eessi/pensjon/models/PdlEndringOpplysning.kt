package no.nav.eessi.pensjon.models

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonTypeId
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endringstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Opplysningstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskAdresse
import java.time.LocalDate

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

interface  Endringsmelding{
    val kilde: String
    val type: String
}
@JsonTypeInfo( use = JsonTypeInfo.Id.NAME, defaultImpl = EndringsmeldingUID::class )
data class EndringsmeldingUID(
    @JsonTypeId
    override val type: String = Opplysningstype.UTENLANDSKIDENTIFIKASJONSNUMMER.name,
    override val kilde: String = "",
    val identifikasjonsnummer: String,
    val utstederland: String
) : Endringsmelding

@JsonTypeInfo( use = JsonTypeInfo.Id.NAME, defaultImpl = EndringsmeldingUtAdresse::class )
data class EndringsmeldingUtAdresse(
    @JsonTypeId
    override val type: String = Opplysningstype.KONTAKTADRESSE.name,
    override val kilde: String = "",
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val gyldigFraOgMed: LocalDate?,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val gylidgTilOgMed: LocalDate?,
    val coAdressenavn: String?,
    val adresse: UtenlandskAdresse?
) : Endringsmelding