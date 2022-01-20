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
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient
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
//        val potensiellePersonRelasjoner = seder.flatMap { (docitem, sed) -> RelasjonsHandler.hentRelasjoner(sed, rinaDocumentId, bucType) }

        //slå opp PDL
        return hentIdentifisertePersoner(potensiellePersonRelasjoner, rinaDocumentId)

    }


    fun hentIdentifisertePersoner(
        potensielleFnr: List<SEDPersonRelasjon?>,
        rinaDocumentId: String
    ): List<IdentifisertPerson> {

        return potensielleFnr.filterNotNull().distinctBy { relasjon -> relasjon.fnr?.value }.mapNotNull { identifiserPerson(it) }

    }

    fun identifiserPerson(relasjon: SEDPersonRelasjon): IdentifisertPerson? {
        logger.debug("Henter ut følgende personRelasjon: ${relasjon.toJson()}")

        return try {
//            personService.hentPersonUtenlandskIdent(NorskIdent(fodselsnummer.value)) utgåååååår

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
            hentLandkode(person),
            person.geografiskTilknytning?.gtKommune ?: person.geografiskTilknytning?.gtBydel,
            finnesPersonMedAdressebeskyttelse(relasjon.fnr!!),
            null,
            relasjon
        ).also { logger.debug("Følgende populert Person: $it") }
    }

    fun finnesPersonMedAdressebeskyttelse(fodselsnummer: Fodselsnummer): Boolean {
        val fnr = listOf(fodselsnummer.value)
        val gradering = listOf(AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND, AdressebeskyttelseGradering.STRENGT_FORTROLIG)
        return personService.harAdressebeskyttelse(fnr, gradering)
    }

    private fun hentLandkode(person: Person): String {
        val landkodeOppholdKontakt = person.kontaktadresse?.utenlandskAdresseIFrittFormat?.landkode
        val landkodeUtlandsAdresse = person.kontaktadresse?.utenlandskAdresse?.landkode
        val landkodeOppholdsadresse = person.oppholdsadresse?.utenlandskAdresse?.landkode
        val landkodeBostedsadresse = person.bostedsadresse?.utenlandskAdresse?.landkode
        val geografiskLandkode = person.geografiskTilknytning?.gtLand
        val landkodeBostedNorge = person.bostedsadresse?.vegadresse
        val landkodeKontaktNorge = person.kontaktadresse?.postadresseIFrittFormat

        logger.debug("Landkode og person: ${person.toJson()}")

        return when {
            landkodeOppholdKontakt != null -> {
                logger.info("Velger landkode fra kontaktadresse.utenlandskAdresseIFrittFormat ")
                landkodeOppholdKontakt
            }
            landkodeUtlandsAdresse != null -> {
                logger.info("Velger landkode fra kontaktadresse.utenlandskAdresse")
                landkodeUtlandsAdresse
            }
            landkodeOppholdsadresse != null -> {
                logger.info("Velger landkode fra oppholdsadresse.utenlandskAdresse")
                landkodeOppholdsadresse
            }
            landkodeBostedsadresse != null -> {
                logger.info("Velger landkode fra bostedsadresse.utenlandskAdresse")
                landkodeBostedsadresse
            }
            geografiskLandkode != null -> {
                logger.info("Velger landkode fra geografiskTilknytning.gtLand")
                geografiskLandkode
            }
            landkodeBostedNorge != null -> {
                logger.info("Velger landkode NOR fordi  bostedsadresse.vegadresse ikke er tom")
                "NOR"
            }
            landkodeKontaktNorge != null -> {
                logger.info("Velger landkode NOR fordi  kontaktadresse.postadresseIFrittFormat ikke er tom")
                "NOR"
            }
            else -> {
                logger.info("Velger tom landkode siden ingen særregler for adresseutvelger inntraff")
                ""
            }
        }
    }

}



