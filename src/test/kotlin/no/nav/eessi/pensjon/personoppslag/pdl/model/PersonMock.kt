package no.nav.eessi.pensjon.personoppslag.pdl.model

import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import java.time.LocalDateTime

object PersonMock {
    internal fun createWith(
        fnr: String? = null,
        landkoder: Boolean = true,
        fornavn: String = "Test",
        etternavn: String = "Testesen",
        aktoerId: AktoerId? = null,
        geo: String? = "0301",
        uid: List<UtenlandskIdentifikasjonsnummer> = emptyList()
    ): Person {

        val foedselsdato = fnr?.let { Fodselsnummer.fra(it)?.getBirthDate() }
        val utenlandskadresse = if (landkoder) null else UtenlandskAdresse(landkode = "SWE")

        val identer = listOfNotNull(
            aktoerId?.let { IdentInformasjon(ident = it.id, gruppe = IdentGruppe.AKTORID) },
            fnr?.let { IdentInformasjon(ident = it, gruppe = IdentGruppe.FOLKEREGISTERIDENT) }
        )

        val metadata = Metadata(
            listOf(
                Endring(
                    "kilde",
                    LocalDateTime.now(),
                    "ole",
                    "system1",
                    Endringstype.OPPRETT
                )
            ),
            false,
            "nav",
            "1234"
        )

        return Person(
            identer = identer,
            navn = Navn(fornavn, null, etternavn, metadata = metadata),
            adressebeskyttelse = listOf(AdressebeskyttelseGradering.UGRADERT),
            bostedsadresse = Bostedsadresse(
                gyldigFraOgMed = LocalDateTime.now(),
                gyldigTilOgMed = LocalDateTime.now(),
                vegadresse = Vegadresse("Oppoverbakken", "66", null, "1920"),
                utenlandskAdresse = utenlandskadresse,
                metadata
            ),
            oppholdsadresse = null,
            statsborgerskap = emptyList(),
            foedsel = Foedsel(foedselsdato, null, null, null, metadata = metadata),
            geografiskTilknytning = geo?.let { GeografiskTilknytning(GtType.KOMMUNE, it, null, null) },
            kjoenn = Kjoenn(KjoennType.KVINNE, null, metadata),
            doedsfall = null,
            forelderBarnRelasjon = emptyList(),
            sivilstand = emptyList(),
            kontaktadresse = null,
            kontaktinformasjonForDoedsbo = null,
            utenlandskIdentifikasjonsnummer = uid
        )
    }

    internal fun createBrukerWithUid(
        fnr: String?,
        fornavn: String = "Fornavn",
        etternavn: String = "Etternavn",
        uid: List<UtenlandskIdentifikasjonsnummer> = emptyList(),
    ): PersonUtenlandskIdent {

        val identer = listOfNotNull(
            fnr?.let { IdentInformasjon(ident = it, gruppe = IdentGruppe.FOLKEREGISTERIDENT) }
        , IdentInformasjon("65466565", IdentGruppe.AKTORID))

        val metadata = createMetadata()

        return PersonUtenlandskIdent(
            identer = identer,
            navn = Navn(
                fornavn = fornavn, etternavn = etternavn, metadata = metadata
            ),
            kjoenn = Kjoenn(KjoennType.KVINNE, metadata = metadata),
            utenlandskIdentifikasjonsnummer = uid

        )
    }
    internal fun createMetadata() : Metadata {
        return Metadata(
            listOf(
                Endring(
                    "kilde",
                    LocalDateTime.now(),
                    "ole",
                    "system1",
                    Endringstype.OPPRETT
                )
            ),
            false,
            "nav",
            "1234"
        )
    }
}