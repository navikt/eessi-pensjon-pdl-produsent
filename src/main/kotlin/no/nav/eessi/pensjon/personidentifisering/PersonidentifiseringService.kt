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
import no.nav.eessi.pensjon.personoppslag.pdl.model.Person
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PersonidentifiseringService(private val personService: PersonService) {

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
        potensiellePersonRelasjoner: List<PersonIdentier>,
        rinaDocumentId: String
    ): List<IdentifisertPerson> {

        val distinctByPotensielleSEDPersonRelasjoner = potensiellePersonRelasjoner.distinctBy { relasjon -> relasjon.fnr }

        logger.info("Forsøker å identifisere personer ut fra følgende SED: ${distinctByPotensielleSEDPersonRelasjoner.map { "${it.sedType}, ${it.uid} "}}, BUC: $bucType")

            return distinctByPotensielleSEDPersonRelasjoner
                .mapNotNull { relasjon ->

                    identifiserPerson(relasjon)

            }
            .distinctBy { it.personRelasjon.fnr }

    }

    fun identifiserPerson(personIdenter: PersonIdentier): IdentifisertPerson? {
        logger.debug("Henter ut følgende personRelasjon: ${personIdenter.toJson()}")

        return try {
            logger.info("Velger fnr: ${personIdenter.fnr}, uid: ${personIdenter.uid}, i SED: ${personIdenter.sedType}")

            val valgtFnr = personIdenter.fnr?.value
            if (valgtFnr == null) {
                logger.info("Ingen gyldig ident, går ut av hentIdentifisertPerson!")
                return null
            }

            personService.hentPerson(NorskIdent(valgtFnr))
                ?.let { person ->
                    populerIdentifisertPerson(
                        person,
                        personIdenter,
                    )
                }
                ?.also {
                    logger.debug(""" IdentifisertPerson hentet fra PDL
                                     navn: ${it.personNavn}, sed: ${it.personRelasjon})""".trimIndent()
                    )
                }
        } catch (ex: Exception) {
            logger.warn("Feil ved henting av person fra PDL (ep-personoppslag), fortsetter uten", ex)
            null
        }
    }

    private fun populerIdentifisertPerson(
        person: Person,
        personIdentier: PersonIdentier,
    ): IdentifisertPerson {
        logger.debug("Populerer IdentifisertPerson med data fra PDL")

        val personNavn = person.navn?.run { "$fornavn $etternavn" }
        val personFnr = person.identer.first { it.gruppe == IdentGruppe.FOLKEREGISTERIDENT }.ident
        val newPersonRelasjon = personIdentier.copy(fnr = Fodselsnummer.fra(personFnr))

        return IdentifisertPerson(
            personNavn,
            newPersonRelasjon
        )
    }

//    /**
//     * Forsøker å finne om identifisert person er en eller fler med avdød person
//     */
//    /**
//     * Forsøker å finne om identifisert person er en eller fler med avdød person
//     */
//    fun identifisertPersonUtvelger(
//        identifisertePersoner: List<IdentifisertPerson>,
//        bucType: BucType,
//        sedType: SedType?,
//        potensiellePersonRelasjoner: List<PersonIdentier>
//    ): IdentifisertPerson? {
//        logger.info("Antall identifisertePersoner : ${identifisertePersoner.size} ")
//
//        val forsikretPerson = brukForsikretPerson(sedType, identifisertePersoner)
//        if (forsikretPerson != null)
//            return forsikretPerson
//
//        return when {
//            identifisertePersoner.isEmpty() -> null
//            bucType == BucType.R_BUC_02 -> identifisertePersoner.first()
//            bucType == BucType.P_BUC_02 -> identifisertePersoner.firstOrNull { it.personRelasjon.relasjon == Relasjon.GJENLEVENDE }
//            bucType == BucType.P_BUC_05 -> {
//                val erGjenlevendeRelasjon = potensiellePersonRelasjoner.any { it.relasjon == Relasjon.GJENLEVENDE }
//                utvelgerPersonOgGjenlev(identifisertePersoner, erGjenlevendeRelasjon)
//            }
//            bucType == BucType.P_BUC_10 -> {
//                val erGjenlevendeYtelse = potensiellePersonRelasjoner.any { it.saktype == Saktype.GJENLEV }
//
//                utvelgerPersonOgGjenlev(identifisertePersoner, erGjenlevendeYtelse)
//            }
//            //buc_01,buc_03 hvis flere enn en forsikret person så sendes til id_og_fordeling
//            bucType == BucType.P_BUC_01 && (identifisertePersoner.size > 1) -> throw FlerePersonPaaBucException()
//            bucType == BucType.P_BUC_03 && (identifisertePersoner.size > 1) -> throw FlerePersonPaaBucException()
//
//            identifisertePersoner.size == 1 -> identifisertePersoner.first()
//            else -> {
//                logger.debug("BucType: $bucType Personer: ${identifisertePersoner.toJson()}")
//                throw RuntimeException("Stopper grunnet flere personer på bucType: $bucType")
//            }
//        }
//    }

//    //felles for P_BUC_05 og P_BUC_10
//    private fun utvelgerPersonOgGjenlev(
//        identifisertePersoner: List<IdentifisertPerson>,
//        erGjenlevende: Boolean
//    ): IdentifisertPerson? {
//        identifisertePersoner.forEach {
//            logger.debug(it.toJson())
//        }
//        val forsikretPerson = identifisertePersoner.firstOrNull { it.personRelasjon.relasjon == Relasjon.FORSIKRET }
//        val gjenlevendePerson = identifisertePersoner.firstOrNull { it.personRelasjon.relasjon == Relasjon.GJENLEVENDE }
//        logger.debug("personident: ${forsikretPerson?.personRelasjon?.fnr}, gjenlevident: ${gjenlevendePerson?.personRelasjon?.fnr} , harGjenlvRelasjon: $erGjenlevende")
//
//        return when {
//            gjenlevendePerson != null -> gjenlevendePerson
//            erGjenlevende -> null
//            else -> null
//        }
//    }

//    /**
//     * Noen Seder kan kun inneholde forsikret person i de tilfeller benyttes den forsikrede selv om andre Sed i Buc inneholder andre personer
//     */
//    /**
//     * Noen Seder kan kun inneholde forsikret person i de tilfeller benyttes den forsikrede selv om andre Sed i Buc inneholder andre personer
//     */
//    private fun brukForsikretPerson(
//        sedType: SedType?,
//        identifisertePersoner: List<IdentifisertPerson>
//    ): IdentifisertPerson? {
//        if (sedType in brukForikretPersonISed) {
//            logger.info("Henter ut forsikret person fra følgende SED $sedType")
//            return identifisertePersoner.firstOrNull { it.personRelasjon.relasjon == Relasjon.FORSIKRET }
//        }
//        return null
//    }

}



