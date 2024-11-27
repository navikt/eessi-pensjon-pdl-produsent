package no.nav.eessi.pensjon.pdl

import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.sed.*
import no.nav.eessi.pensjon.personoppslag.pdl.model.*
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.utils.mapJsonToAny
import java.time.LocalDate
import java.time.LocalDateTime


const val FNR = "11067122781"
const val FNR_MED_MELLOMROM = "110671 22781"
const val DNR = "51077403071"
const val SVENSK_FNR = "512020-1234"
const val FINSK_FNR = "130177-308T"
const val SOME_FNR = "11077403071"
const val AKTOERID = "32165498732"
open class IdentBaseTest {
    fun pdlEndringsMelding(
        fnr: String,
        utenlandskId: String = SVENSK_FNR,
        utstederland: String,
        utenlandskInstitusjon: String? = "Utenlandsk institusjon"
    ) =
        PdlEndringOpplysning(
            listOf(
                Personopplysninger(
                    endringstype = Endringstype.OPPRETT,
                    ident = fnr,
                    endringsmelding = EndringsmeldingUID(
                        identifikasjonsnummer = utenlandskId,
                        utstederland = utstederland,
                        kilde = utenlandskInstitusjon!!
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
        sedType: SedType = SedType.SEDTYPE_P2000,
        avsenderLand: String = "SE",
        avsenderNavn: String? = "Utenlandsk institusjon",
        navBruker: Fodselsnummer? = null
    ) = SedHendelse(
        sektorKode = "P",
        avsenderLand = avsenderLand,
        bucType = bucType,
        rinaSakId = "123456479867",
        rinaDokumentId = "SOME_DOKUMENT_ID",
        rinaDokumentVersjon = "SOME_RINADOKUMENT_VERSION",
        sedType = sedType,
        avsenderNavn = avsenderNavn,
        navBruker = navBruker
    )

    fun sed(
        brukersAdresse: Adresse? = null,
        pinItem: List<PinItem>?,
        pensjon: Pensjon = Pensjon(),
        sedType: SedType = SedType.SEDTYPE_P2100
    ) = SED (
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
        brukersAdresse: Adresse? = null,
        pinItem: List<PinItem>?,
        fodselsdato: String?
    ) = sed(
        brukersAdresse, pinItem, Pensjon(
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
                    foedselsdato = fodselsdato,
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
    ): PdlPerson = PdlPerson(
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
            SedType.SEDTYPE_P4000 -> mapJsonToAny<P4000>(json)
            SedType.SEDTYPE_P5000 -> mapJsonToAny<P5000>(json)
            SedType.SEDTYPE_P6000 -> mapJsonToAny<P6000>(json)
            SedType.SEDTYPE_P7000 -> mapJsonToAny<P7000>(json)
            SedType.SEDTYPE_P8000 -> mapJsonToAny<P8000>(json)
            SedType.SEDTYPE_P9000 -> mapJsonToAny<P9000>(json)
            SedType.SEDTYPE_P10000 -> mapJsonToAny<P10000>(json)
            SedType.SEDTYPE_P15000 -> mapJsonToAny<P15000>(json)
            else -> SED.fromJson(json)
        }
    }
}
