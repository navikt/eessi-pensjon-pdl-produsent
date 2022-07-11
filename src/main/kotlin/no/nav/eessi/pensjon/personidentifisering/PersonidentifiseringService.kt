package no.nav.eessi.pensjon.personidentifisering

import no.nav.eessi.pensjon.eux.model.SedType
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
import no.nav.eessi.pensjon.klienter.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PersonidentifiseringService(private val personService: PersonService, private val kodeverk: KodeverkClient) {

    private val logger = LoggerFactory.getLogger(PersonidentifiseringService::class.java)

    fun hentIdentifisertPersoner(
        seder: List<Pair<ForenkletSED, SED>>,
        bucType: BucType,
        sedType: SedType?,
        rinaDocumentId: String
    ): List<IdentifisertPerson> {

        val sedIBUC = seder.map { (item, sed) -> Pair(item.id, sed) }
        val potensiellePersonRelasjoner = RelasjonsHandler.hentRelasjoner(sedIBUC, bucType)

        //slå opp PDL
        return hentIdentifisertePersoner(potensiellePersonRelasjoner, rinaDocumentId)

    }


    fun hentIdentifisertePersoner(
        potensielleFnr: List<SEDPersonRelasjon?>,
        rinaDocumentId: String
    ): List<IdentifisertPerson> {

        return potensielleFnr
            .filterNotNull()
            .distinctBy { relasjon -> relasjon.fnr?.value }
            .mapNotNull { relasjon -> relasjon.fnr?.let { identifiserPerson(relasjon) } }

    }

    fun identifiserPerson(relasjon: SEDPersonRelasjon): IdentifisertPerson? {
        return try {
            personService.hentPerson(NorskIdent(relasjon.fnr!!.value))
                ?.let { person ->
                    populerIdentifisertPerson(
                        person,
                        relasjon
                    )
                }
        } catch (ex: Exception) {
            logger.warn("Feil ved henting av person fra PDL (ep-personoppslag), fortsetter uten", ex)
            null
        }
    }

    private fun populerIdentifisertPerson(
        person: Person,
        relasjon: SEDPersonRelasjon,
    ): IdentifisertPerson {
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
            person.erDoed()
        ).also { logger.debug("Følgende populert Person: $it") }
    }

    fun finnesPersonMedAdressebeskyttelse(fodselsnummer: Fodselsnummer): Boolean {
        val fnr = listOf(fodselsnummer.value)
        val gradering = listOf(AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND, AdressebeskyttelseGradering.STRENGT_FORTROLIG)
        return personService.harAdressebeskyttelse(fnr, gradering)
    }
}
