package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.personidentifisering.PersonIdentier
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer

abstract class GjenlevendeHvisFinnes() : AbstractIdent() {

    fun hentRelasjonGjenlevendeFnrHvisFinnes(gjenlevendeBruker: Bruker? = null, sedType: SedType) : List<PersonIdentier> {
        logger.info("Leter etter gyldig identer i SedType: $sedType")

        val gjenlevendePerson = gjenlevendeBruker?.person
        logger.debug("Hva er gjenlevendePerson pin?: ${gjenlevendePerson?.pin}")

        gjenlevendePerson?.let { gjenlevendePerson ->
            val gjenlevendePin = Fodselsnummer.fra(gjenlevendePerson.pin?.firstOrNull { it.land == "NO" }?.identifikator)
            val pinItemUtlandList = gjenlevendePerson.pin?.filterNot { it.land == "NO" }

            val gjenlevendeRelasjon = gjenlevendePerson.relasjontilavdod?.relasjon
            logger.info("Innhenting av relasjon: $gjenlevendeRelasjon, sedType: $sedType")

            return listOf(
                PersonIdentier(
                    gjenlevendePin,
                    pinItemUtlandList,
                    sedType = sedType,
                ))

        }

        return emptyList()
    }

}