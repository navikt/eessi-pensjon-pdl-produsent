package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.personidentifisering.PersonIdenter
import no.nav.eessi.pensjon.personidentifisering.UtenlandskPin
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer

class Forsikret() {

    fun hentForsikretPerson(sed: SED): List<PersonIdenter> {
        val forsikretPerson = sed.nav?.bruker?.person
        logger.info("Leter etter gyldig ident og relasjon(er) i SedType: ${sed.type}")

        forsikretPerson?.let { person ->
            val fodselnummer = Fodselsnummer.fra(person.pin?.firstOrNull { it.land == "NO" }?.identifikator)

            val pinItemUtlandList = person.pin?.filterNot { it.land == "NO" }
                ?.filter { it.land != null && it.identifikator != null && it.institusjonsnavn != null }
                ?.map { UtenlandskPin(it.institusjonsnavn!!, it.identifikator!!, it.land!!) }

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