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

    private val brukForikretPersonISed = listOf(SedType.H121, SedType.H120, SedType.H070)

    fun hentIdentifisertPersoner(
        sed: SED,
        bucType: BucType,
        sedType: SedType?,
        rinaDocumentId: String
    ): List<IdentifisertPerson>? {

        //fin norskident og utlandskeidenter
        val potensiellePersonRelasjoner = RelasjonsHandler.hentRelasjoner(sed, rinaDocumentId, bucType)

        //slå opp PDL
        return hentIdentifisertePersoner(bucType, potensiellePersonRelasjoner, rinaDocumentId)

    }

    fun hentIdentifisertePersoner(
        bucType: BucType,
        potensiellePersonRelasjoner: List<PersonIdenter>,
        rinaDocumentId: String
    ): List<IdentifisertPerson> {

        val distinctByPotensielleSEDPersonRelasjoner = potensiellePersonRelasjoner.distinctBy { relasjon -> relasjon.fnr }

        logger.info("Forsøker å identifisere personer ut fra følgende SED: ${distinctByPotensielleSEDPersonRelasjoner.map { "${it.sedType}, ${it.uid} "}}, BUC: $bucType")

            return distinctByPotensielleSEDPersonRelasjoner
                .mapNotNull { relasjon ->

                    identifiserPerson(relasjon)

            }
            .distinctBy { it.personIdenter.fnr }

    }

    fun identifiserPerson(personIdenter: PersonIdenter): IdentifisertPerson? {
        logger.debug("Henter ut følgende personRelasjon: ${personIdenter.toJson()}")

        return try {
            logger.info("Velger fnr: ${personIdenter.fnr}, uid: ${personIdenter.uid}, i SED: ${personIdenter.sedType}")

            val valgtFnr = personIdenter.fnr?.value
            if (valgtFnr == null) {
                logger.info("Ingen gyldig ident, går ut av hentIdentifisertPerson!")
                return null
            }

            personService.hentPersonUtenlandskIdent(NorskIdent(valgtFnr))
                ?.let { person ->
                    populerIdentifisertPerson(
                        person,
                        personIdenter,
                    )
                }
                ?.also {
                    logger.debug(""" IdentifisertPerson hentet fra PDL
                                     navn: ${it.personNavn}, sed: ${it.personIdenter})""".trimIndent()
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

        val personNavn = person.navn?.run { "$fornavn $etternavn" }
        val personFnr = person.identer.first { it.gruppe == IdentGruppe.FOLKEREGISTERIDENT }.ident
        val newPersonRelasjon = personIdentier.copy(fnr = Fodselsnummer.fra(personFnr))

        return IdentifisertPerson(
            personNavn,
            newPersonRelasjon,
            kjoenn = person.kjoenn?.kjoenn,
            uid = person.utenlandskIdentifikasjonsnummer
        )
    }

//    fun valider(identifisertPerson: List<IdentifisertPerson>) : List<PersonIdentValidering>? {
//
//        return identifisertPerson
//    }

}



