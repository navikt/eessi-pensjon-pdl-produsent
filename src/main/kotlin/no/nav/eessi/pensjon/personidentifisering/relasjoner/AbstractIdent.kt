package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.personidentifisering.PersonIdenter
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val logger: Logger = LoggerFactory.getLogger(AbstractIdent::class.java)



abstract class AbstractIdent() {

    abstract fun hentRelasjoner(sed: SED): List<PersonIdenter>

    fun hentForsikretPerson(sed: SED): List<PersonIdenter> {
        val forsikretPerson = sed.nav?.bruker?.person
        logger.info("Leter etter gyldig ident og relasjon(er) i SedType: ${sed.type}")

        forsikretPerson?.let { person ->
            val fodselnummer = Fodselsnummer.fra(person.pin?.firstOrNull { it.land == "NO" }?.identifikator)
            val pinItemUtlandList = person.pin?.filterNot { it.land == "NO" }

            logger.debug("Legger til person forsikret og sedType: ${sed.type}, fnr: $fodselnummer, uid: $pinItemUtlandList")
            return listOf(
                PersonIdenter(
                    fodselnummer, pinItemUtlandList, sedType = sed.type
                )
            )
        }

        logger.warn("Ingen forsikret person funnet")
        return emptyList()
    }
}
