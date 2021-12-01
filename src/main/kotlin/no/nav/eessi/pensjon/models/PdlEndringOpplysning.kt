package no.nav.eessi.pensjon.models

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

data class Endringsmelding(
    val type: String = "UTENLANDSKIDENTIFIKASJONSNUMMER",
    val identifikasjonsnummer: String,
    val utstederland: String,
    val kilde: String
)

/*

{
    "personopplysninger": [
    {
        "endringsmelding": {
        "@type": "UTENLANDSKIDENTIFIKASJONSNUMMER",
        "identifikasjonsnummer": "123456-1234",
        "utstederland": "SWE",
        "kilde": "Sverige"
    },
        "ident": "01234567890",
        "endringstype": "OPPRETT",
        "opplysningstype": "UTENLANDSKIDENTIFIKASJONSNUMMER"
    }
    ]
}
*/

//    "personopplysninger": [
//    {
//        "endringstype": "OPPRETT",
//        "ident": "string",
//        "opplysningstype": "UTENLANDSKIDENTIFIKASJONSNUMMER",
//        "endringsmelding": {
//        "@type": "KONTAKTADRESSE",
//        "kilde": "Krankenkasse"
//    },
//        "opplysningsId": "string"
//    }
//    ]
//}

/*
UtenlandskIdentifikasjonsnummer{
    description:
    Endringsmelding av Utenlandsk identifikasjonsnummer.
    @type*	string
            example: KONTAKTADRESSE
    Navnet på opplysningstypen i upper-case.
    Enum:
    [ UTENLANDSKIDENTIFIKASJONSNUMMER, TELEFONNUMMER, SIKKERHETSTILTAK, NAVN, DOED, STATSBORGERSKAP, KONTONUMMER, KJOENN, FOEDSELSDATO, SPRAAK_MAALFORM, KONTAKTADRESSE, TILRETTELAGT_KOMMUNIKASJON, FAMILIERELASJON, SIVILSTAND, FULLMAKT, NAV_PERSON, ADRESSEBESKYTTELSE, BOSTEDSADRESSE, OPPHOLDSADRESSE, ANNULLER, OPPHOER, NORSK_KONTAKTADRESSE, NORSK_POSTBOKSADRESSE, NORSK_STEDSADRESSE, NORSK_GATEADRESSE, UTENLANDSK_KONTAKTADRESSE, MIDLERTIDIG_ADRESSE_UTBETALING ]
    kilde*	string
    example: Krankenkasse
    Hvor opplysningen kommer fra. Kilden kan være en utenlandsk myndighet, en institusjon eller en bruker.
    identifikasjonsnummer*	string
    example: 170590-1234
    Selve identifikasjonsnummeret til Utenlandsk IDen
    opphoersdato	string($date)
    example: 2018-01-31
    Opphørsdato (ISO-8601) for opplysningen. Denne skal være null med mindre opplysningen skal registreres som opphørt. Format: yyyy-MM-dd
    utstederland*	string
    example: POL
    Landkode for hvilket land som utstedte Utlandsk IDen (Hentes fra felleskodeverk)

*/