package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer

class Gjenlevende() {

    fun hentRelasjonGjenlevendeFnrHvisFinnes(gjenlevendeBruker: Bruker? = null, sedType: SedType): List<IdentifisertPerson> {
        logger.info("Leter etter gyldig identer i SedType: $sedType")

        val gjenlevendePerson = gjenlevendeBruker?.person
        logger.debug("Hva er gjenlevendePerson pin?: ${gjenlevendePerson?.pin}")

        gjenlevendePerson?.let { gjenlevendePerson ->
            val gjenlevendePin =
                Fodselsnummer.fra(gjenlevendePerson.pin?.firstOrNull { it.land == "NO" }?.identifikator)

            val pinItemUtlandList = UtlandMapping().mapUtenlandsPin(gjenlevendePerson)

            val gjenlevendeRelasjon = gjenlevendePerson.relasjontilavdod?.relasjon
            logger.info("Innhenting av relasjon: $gjenlevendeRelasjon, sedType: $sedType")

            return listOf(
                IdentifisertPerson(
                    gjenlevendePin,
                )
            )
        }
        return emptyList()
    }
}