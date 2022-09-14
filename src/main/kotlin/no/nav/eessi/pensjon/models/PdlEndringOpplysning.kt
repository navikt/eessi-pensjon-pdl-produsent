package no.nav.eessi.pensjon.models

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeId
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endringstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Opplysningstype
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

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY)
@JsonSubTypes(JsonSubTypes.Type(value = Opplysningstype::class))
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
) : Endringsmelding {
    override fun toString(): String {
        return "EndringsmeldingUID(type='$type', kilde='$kilde', identifikasjonsnummer= 'vises ikke i logg', utstederland='$utstederland')"
    }
}

@JsonTypeInfo( use = JsonTypeInfo.Id.NAME, defaultImpl = EndringsmeldingKontaktAdresse::class )
data class EndringsmeldingKontaktAdresse(
    @JsonTypeId
    override val type: String = Opplysningstype.KONTAKTADRESSE.name,
    override val kilde: String = "",
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val gyldigFraOgMed: LocalDate?,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    val gyldigTilOgMed: LocalDate?,
    val coAdressenavn: String?,
    val adresse: EndringsmeldingUtenlandskAdresse?
) : Endringsmelding

@JsonTypeInfo( use = JsonTypeInfo.Id.NAME, defaultImpl = EndringsmeldingUtenlandskAdresse::class )
data class EndringsmeldingUtenlandskAdresse(
    @JsonTypeId
    val type: String = "UTENLANDSK_ADRESSE",
    val adressenavnNummer: String? = null,
    val bySted: String? = null,
    val bygningEtasjeLeilighet: String? = null,
    val landkode: String,
    val postboksNummerNavn: String? = null,
    val postkode: String? = null,
    val regionDistriktOmraade: String? = null
)

