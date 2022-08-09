package no.nav.eessi.pensjon.personidentifisering

import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.personidentifisering.relasjoner.RelasjonsHandler
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.AdressebeskyttelseGradering
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.Person
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PersonidentifiseringService(private val personService: PersonService) {

    private val logger = LoggerFactory.getLogger(PersonidentifiseringService::class.java)

    fun hentIdentifisertePersoner(seder: List<Pair<ForenkletSED, SED>>, bucType: BucType): List<IdentifisertPerson> {
        val sedIBUC = seder.map { (item, sed) -> Pair(item.id, sed) }
        return RelasjonsHandler.hentRelasjoner(sedIBUC, bucType)
            .distinctBy { relasjon -> relasjon.fnr?.value }
            .mapNotNull { relasjon -> relasjon.fnr?.let { identifiserPerson(relasjon) } }
    }

    private fun identifiserPerson(relasjon: SEDPersonRelasjon): IdentifisertPerson? =
        try {
            personService.hentPerson(NorskIdent(relasjon.fnr!!.value))
                ?.let { person -> populerIdentifisertPerson(person, relasjon) }
        } catch (ex: Exception) {
            logger.warn("Feil ved henting av person fra PDL (ep-personoppslag), fortsetter uten", ex)
            null
        }

    private fun populerIdentifisertPerson(person: Person, relasjon: SEDPersonRelasjon, ): IdentifisertPerson {
        logger.debug("Populerer IdentifisertPerson med data fra PDL, person: $person")

        return IdentifisertPerson(
            relasjon.fnr,
            person.utenlandskIdentifikasjonsnummer,
            person.identer.first { it.gruppe == IdentGruppe.AKTORID }.ident,
            person.landkode(),
            person.geografiskTilknytning?.gtKommune ?: person.geografiskTilknytning?.gtBydel,
            finnesPersonMedAdressebeskyttelse(relasjon.fnr!!),
            null,
            relasjon,
            person.erDoed(),
            person.kontaktadresse!!
        ).also { logger.debug("Følgende populert Person: $it") }
    }

    private fun finnesPersonMedAdressebeskyttelse(fodselsnummer: Fodselsnummer): Boolean =
        personService.harAdressebeskyttelse(
            listOf(fodselsnummer.value),
            listOf(AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND, AdressebeskyttelseGradering.STRENGT_FORTROLIG)
        )
}
