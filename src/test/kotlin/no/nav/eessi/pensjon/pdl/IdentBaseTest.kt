package no.nav.eessi.pensjon.pdl

import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.sed.Adresse
import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.Nav
import no.nav.eessi.pensjon.eux.model.sed.P10000
import no.nav.eessi.pensjon.eux.model.sed.P15000
import no.nav.eessi.pensjon.eux.model.sed.P4000
import no.nav.eessi.pensjon.eux.model.sed.P5000
import no.nav.eessi.pensjon.eux.model.sed.P6000
import no.nav.eessi.pensjon.eux.model.sed.P7000
import no.nav.eessi.pensjon.eux.model.sed.P8000
import no.nav.eessi.pensjon.eux.model.sed.P9000
import no.nav.eessi.pensjon.eux.model.sed.Pensjon
import no.nav.eessi.pensjon.eux.model.sed.PinItem
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.models.EndringsmeldingUID
import no.nav.eessi.pensjon.models.PdlEndringOpplysning
import no.nav.eessi.pensjon.models.Personopplysninger
import no.nav.eessi.pensjon.personoppslag.pdl.model.AdressebeskyttelseGradering
import no.nav.eessi.pensjon.personoppslag.pdl.model.Doedsfall
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endringstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Folkeregistermetadata
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.Kontaktadresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.KontaktadresseType
import no.nav.eessi.pensjon.personoppslag.pdl.model.Metadata
import no.nav.eessi.pensjon.personoppslag.pdl.model.Opplysningstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Person
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskAdresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskIdentifikasjonsnummer
import no.nav.eessi.pensjon.utils.mapJsonToAny
import java.time.LocalDate
import java.time.LocalDateTime


const val FNR = "11067122781"
 const val DNR = "51077403071"
 const val SVENSK_FNR = "512020-1234"
 const val FINSK_FNR = "130177-308T"
 const val SOME_FNR = "51077403071"
 const val AKTOERID = "32165498732"
open class IdentBaseTest {
    fun pdlEndringsMelding(
        fnr: String,
        utenlandskId: String = SVENSK_FNR,
        utstederland: String,
        utenlandskInstitusjon: String = "Utenlandsk institusjon"
    ) =
        PdlEndringOpplysning(
            listOf(
                Personopplysninger(
                    endringstype = Endringstype.OPPRETT,
                    ident = fnr,
                    endringsmelding = EndringsmeldingUID(
                        identifikasjonsnummer = utenlandskId,
                        utstederland = utstederland,
                        kilde = utenlandskInstitusjon
                    ),
                    opplysningstype = Opplysningstype.UTENLANDSKIDENTIFIKASJONSNUMMER
                )
            )
        )

    fun utenlandskIdentifikasjonsnummer(fnr: String) = UtenlandskIdentifikasjonsnummer(
        identifikasjonsnummer = fnr,
        utstederland = "POL",
        opphoert = false,
        folkeregistermetadata = Folkeregistermetadata(
            gyldighetstidspunkt = LocalDateTime.now(),
            ajourholdstidspunkt = LocalDateTime.now(),
        ),
        metadata = metadata()
    )

    fun metadata() = Metadata(
        endringer = emptyList(),
        historisk = false,
        master = "PDL",
        opplysningsId = "opplysningsId"
    )

    fun sedHendelse(
        bucType: BucType = BucType.P_BUC_01,
        sedType: SedType = SedType.P2000,
        avsenderLand: String = "SE",
        avsenderNavn: String? = "Utenlandsk institusjon"
    ) = SedHendelse(
        sektorKode = "P",
        avsenderLand = avsenderLand,
        bucType = bucType,
        rinaSakId = "123456479867",
        rinaDokumentId = "SOME_DOKUMENT_ID",
        rinaDokumentVersjon = "SOME_RINADOKUMENT_VERSION",
        sedType = sedType,
        avsenderNavn = avsenderNavn
    )

    fun sed(id: String = SOME_FNR,
            brukersAdresse: Adresse? = null,
            land: String?,
            pinItem: List<PinItem>? = listOf(PinItem(land = land, identifikator = id)),
            pensjon: Pensjon = Pensjon(),
            sedType: SedType = SedType.P2000) = SED(
        type = sedType,
        sedGVer = null,
        sedVer = null,
        nav = Nav(
            eessisak = null,
            bruker = Bruker(
                mor = null,
                far = null,
                person = no.nav.eessi.pensjon.eux.model.sed.Person(
                    pin = pinItem,
                    pinland = null,
                    statsborgerskap = null,
                    etternavn = null,
                    etternavnvedfoedsel = null,
                    fornavn = null,
                    fornavnvedfoedsel = null,
                    tidligerefornavn = null,
                    tidligereetternavn = null,
                    kjoenn = null,
                    foedested = null,
                    foedselsdato = null,
                    sivilstand = null,
                    relasjontilavdod = null,
                    rolle = null
                ),
                adresse = brukersAdresse,
                arbeidsforhold = null,
                bank = null
            )
        ),
        pensjon = pensjon
    )

    fun sedGjenlevende(
        id: String = SOME_FNR,
        brukersAdresse: Adresse? = null,
        land: String?,
        pinItem: List<PinItem>? = listOf(PinItem(land = land, identifikator = id))
    ) = sed(
        id, brukersAdresse, land, pinItem,
        Pensjon(
            gjenlevende = Bruker(
                mor = null,
                far = null,
                person = no.nav.eessi.pensjon.eux.model.sed.Person(
                    pin = pinItem,
                    pinland = null,
                    statsborgerskap = null,
                    etternavn = null,
                    etternavnvedfoedsel = null,
                    fornavn = null,
                    fornavnvedfoedsel = null,
                    tidligerefornavn = null,
                    tidligereetternavn = null,
                    kjoenn = null,
                    foedested = null,
                    foedselsdato = null,
                    sivilstand = null,
                    relasjontilavdod = null,
                    rolle = null
                ),
                adresse = brukersAdresse,
                arbeidsforhold = null,
                bank = null
            )
        )
    )


    fun personFraPDL(
        id: String = SOME_FNR,
        adressebeskyttelse: List<AdressebeskyttelseGradering> = listOf(),
        utenlandskAdresse: UtenlandskAdresse? = null,
        opplysningsId: String = "DummyOpplysningsId",
        gyldigFraOgMed: LocalDateTime = LocalDateTime.now().minusDays(10),
        gyldigTilOgMed: LocalDateTime = LocalDateTime.now().plusDays(10),
        doedsdato: LocalDate? = null
    ): Person = Person(
        identer = listOf(IdentInformasjon(id, IdentGruppe.FOLKEREGISTERIDENT)),
        navn = null,
        adressebeskyttelse = adressebeskyttelse,
        bostedsadresse = null,
        oppholdsadresse = null,
        statsborgerskap = listOf(),
        foedsel = null,
        geografiskTilknytning = null,
        kjoenn = null,
        doedsfall = Doedsfall(
            doedsdato = doedsdato,
            folkeregistermetadata = null,
            metadata= metadata()
        ),
        forelderBarnRelasjon = listOf(),
        sivilstand = listOf(),
        kontaktadresse = Kontaktadresse(
            coAdressenavn = "c/o Anund",
            folkeregistermetadata = null,
            gyldigFraOgMed = gyldigFraOgMed,
            gyldigTilOgMed = gyldigTilOgMed,
            metadata = Metadata(
                endringer = emptyList(),
                historisk = false,
                master = "",
                opplysningsId = opplysningsId
            ),
            type = KontaktadresseType.Utland,
            utenlandskAdresse = utenlandskAdresse
        ),
        kontaktinformasjonForDoedsbo = null,
        utenlandskIdentifikasjonsnummer = listOf()
    )

    fun convertFromSedTypeToSED(json: String, sedType: SedType): SED {
        return when (sedType) {
            SedType.P4000 -> mapJsonToAny<P4000>(json)
            SedType.P5000 -> mapJsonToAny<P5000>(json)
            SedType.P6000 -> mapJsonToAny<P6000>(json)
            SedType.P7000 -> mapJsonToAny<P7000>(json)
            SedType.P8000 -> mapJsonToAny<P8000>(json)
            SedType.P9000 -> mapJsonToAny<P9000>(json)
            SedType.P10000 -> mapJsonToAny<P10000>(json)
            SedType.P15000 -> mapJsonToAny<P15000>(json)
            else -> SED.fromJson(json)
        }
    }
}
