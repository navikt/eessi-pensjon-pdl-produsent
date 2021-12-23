package no.nav.eessi.pensjon.personidentifisering

import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.personidentifisering.relasjoner.RelasjonsHandler
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.PersonUtenlandskIdent
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient
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

        val potensiellePersonRelasjoner = seder.flatMap { (docitem, sed) -> RelasjonsHandler.hentRelasjoner(sed, rinaDocumentId, bucType) }

        //slå opp PDL
        return hentIdentifisertePersoner(potensiellePersonRelasjoner, rinaDocumentId)

    }


    fun hentIdentifisertePersoner(
        potensielleFnr: List<Fodselsnummer?>,
        rinaDocumentId: String
    ): List<IdentifisertPerson> {

        return potensielleFnr.filterNotNull().distinctBy { fnr -> fnr.value }.mapNotNull { identifiserPerson(it) }

    }

    fun identifiserPerson(fodselsnummer: Fodselsnummer): IdentifisertPerson? {
        logger.debug("Henter ut følgende personRelasjon: ${fodselsnummer.toJson()}")

        return try {
            personService.hentPersonUtenlandskIdent(NorskIdent(fodselsnummer.value))
                ?.let { person ->
                    populerIdentifisertPerson(
                        person,
                        fodselsnummer,
                    )
                }
        } catch (ex: Exception) {
            logger.warn("Feil ved henting av person fra PDL (ep-personoppslag), fortsetter uten", ex)
            null
        }
    }

    private fun populerIdentifisertPerson(
        person: PersonUtenlandskIdent,
        fodselsnummer: Fodselsnummer,
    ): IdentifisertPerson {
        logger.debug("Populerer IdentifisertPerson med data fra PDL")

        return IdentifisertPerson(
            fodselsnummer,
            person.utenlandskIdentifikasjonsnummer
        ).also { logger.debug("Følgende populert Person: $it") }
    }


}



