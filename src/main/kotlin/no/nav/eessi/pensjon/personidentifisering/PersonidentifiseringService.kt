package no.nav.eessi.pensjon.personidentifisering

import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.personidentifisering.relasjoner.RelasjonsHandler
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.PersonUtenlandskIdent
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PersonidentifiseringService(private val personService: PersonService, private val kodeverk: KodeverkClient) {

    private val logger = LoggerFactory.getLogger(PersonidentifiseringService::class.java)

    fun hentIdentifisertPersoner(
        seder: List<SED>,
        bucType: BucType,
        sedType: SedType?,
        rinaDocumentId: String
    ): List<IdentifisertPerson> {

        val potensiellePersonRelasjoner = seder.flatMap { RelasjonsHandler.hentRelasjoner(it, rinaDocumentId, bucType) }

        //slå opp PDL
        return hentIdentifisertePersoner(bucType, potensiellePersonRelasjoner, rinaDocumentId)

    }

    fun hentIdentifisertePersoner(
        bucType: BucType,
        potensiellePersonRelasjoner: List<PersonIdenter>,
        rinaDocumentId: String
    ): List<IdentifisertPerson> {

        val distinctByPotensielleSEDPersonRelasjoner = potensiellePersonRelasjoner.distinctBy { relasjon -> relasjon.fnr }
            return distinctByPotensielleSEDPersonRelasjoner
                .mapNotNull { relasjon ->

                    identifiserPerson(relasjon)

            }
            .distinctBy { it.personIdenterFraSed.fnr }
    }

    fun identifiserPerson(personIdenter: PersonIdenter): IdentifisertPerson? {
        logger.debug("Henter ut følgende personRelasjon: ${personIdenter.toJson()}")

        return try {
            val valgtFnr = personIdenter.fnr?.value ?: return null

            personService.hentPersonUtenlandskIdent(NorskIdent(valgtFnr))
                ?.let { person ->
                    populerIdentifisertPerson(
                        person,
                        personIdenter,
                    )
                }
        } catch (ex: Exception) {
            logger.warn("Feil ved henting av person fra PDL (ep-personoppslag), fortsetter uten", ex)
            null
        }
    }

    private fun populerIdentifisertPerson(
        person: PersonUtenlandskIdent,
        personIdentier: PersonIdenter,
    ): IdentifisertPerson {
        logger.debug("Populerer IdentifisertPerson med data fra PDL")

        val personFnr = person.identer.first { it.gruppe == IdentGruppe.FOLKEREGISTERIDENT }.ident
        val newPersonIdenter = personIdentier.copy(fnr = Fodselsnummer.fra(personFnr))

        return IdentifisertPerson(
            newPersonIdenter,
            uidFraPdl = person.utenlandskIdentifikasjonsnummer
        ).also { logger.debug("Følgende populert Person: $it") }
    }


}



