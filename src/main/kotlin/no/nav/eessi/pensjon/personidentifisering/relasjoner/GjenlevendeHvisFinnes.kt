package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import no.nav.eessi.pensjon.personidentifisering.SEDPersonRelasjon
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer

abstract class GjenlevendeHvisFinnes(private val sed: SED, bucType: BucType, private val rinaDocumentId: String) : AbstractRelasjon(sed, bucType, rinaDocumentId) {

    fun hentRelasjonGjenlevendeFnrHvisFinnes(gjenlevendeBruker: Bruker? = null) : List<SEDPersonRelasjon> {
        logger.info("Leter etter gyldig ident og relasjon(er) i SedType: ${sed.type}")

        val sedType = sed.type
        //gjenlevendePerson (søker)
        val gjenlevendePerson = gjenlevendeBruker?.person
        logger.debug("Hva er gjenlevendePerson pin?: ${gjenlevendePerson?.pin}")

        gjenlevendePerson?.let { gjenlevende ->
            val gjenlevendePin = Fodselsnummer.fra(gjenlevende.pin?.firstOrNull { it.land == "NO" }?.identifikator)
            val gjenlevendeFdato = mapFdatoTilLocalDate(gjenlevende.foedselsdato)
            val sokPersonKriterie =  opprettSokKriterie(gjenlevende)

            val gjenlevendeRelasjon = gjenlevende.relasjontilavdod?.relasjon
            logger.info("Innhenting av relasjon: $gjenlevendeRelasjon")

            if (gjenlevendeRelasjon == null) {
                logger.debug("Legger til person ${Relasjon.GJENLEVENDE} med ukjente relasjoner")
                return listOf(SEDPersonRelasjon(gjenlevendePin, Relasjon.GJENLEVENDE, sedType = sedType, sokKriterier = sokPersonKriterie, fdato = gjenlevendeFdato, rinaDocumentId = rinaDocumentId))
            }

            val sakType =  if (erGjenlevendeBarn(gjenlevendeRelasjon)) {
                Saktype.BARNEP
            } else {
                Saktype.GJENLEV
            }
            logger.debug("Legger til person ${Relasjon.GJENLEVENDE} med sakType: $sakType")
            return listOf(SEDPersonRelasjon(gjenlevendePin, Relasjon.GJENLEVENDE, sakType, sedType = sedType, sokKriterier = sokPersonKriterie, gjenlevendeFdato, rinaDocumentId= rinaDocumentId))
        }
        return emptyList()
    }

    private fun erGjenlevendeBarn(relasjon: String): Boolean {
        val gyldigeBarneRelasjoner = listOf("EGET_BARN", "06", "ADOPTIVBARN", "07", "FOSTERBARN", "08", "STEBARN", "09")
        return relasjon in gyldigeBarneRelasjoner
    }
}