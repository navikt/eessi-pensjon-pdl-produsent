package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.personidentifisering.PersonIdentier
import no.nav.eessi.pensjon.personidentifisering.Rolle.BARN
import no.nav.eessi.pensjon.personidentifisering.Rolle.ETTERLATTE
import no.nav.eessi.pensjon.personidentifisering.Rolle.FORSORGER
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer


class P8000AndP10000Relasjon(private val sed: SED, private val bucType: BucType, private val rinaDocumentId: String): AbstractRelasjon(sed, bucType, rinaDocumentId) {

    override fun hentRelasjoner(): List<PersonIdentier> {
        val fnrListe = mutableListOf<PersonIdentier>()
        logger.info("Leter etter gyldig ident og relasjon(er) i SedType: ${sed.type}")


        val forsikret = hentForsikretPerson()

        hentAnnenpersonRelasjon()?.let { fnrListe.add(it) }

        logger.debug("fnrListe: $fnrListe")

//        if (fnrListe.firstOrNull { it.relasjon == Relasjon.BARN || it.relasjon == Relasjon.FORSORGER } != null ) {
//            return fnrListe + forsikret
//        }
//        return fnrListe.ifEmpty { forsikret }

        return fnrListe
    }

    //Annenperson søker/barn o.l
    fun hentAnnenpersonRelasjon(): PersonIdentier? {
        if (bucType == BucType.P_BUC_05 || bucType == BucType.P_BUC_10 || bucType == BucType.P_BUC_02) {
            val annenPerson = sed.nav?.annenperson?.person

            logger.debug("annenPerson: $annenPerson")
            annenPerson?.let { person ->
                val annenPersonPin = Fodselsnummer.fra(person.pin?.firstOrNull { it.land == "NO" }?.identifikator)
                val pinItemUtlandList = person.pin?.filterNot { it.land == "NO" }
                val rolle = person.rolle

                val annenPersonRelasjon = when (rolle) {
                    //Rolle barn benyttes ikke i noe journalføring hendelse kun hente ut for...?
                    BARN.kode -> PersonIdentier(
                        annenPersonPin,
                        pinItemUtlandList,
                        sedType = sed.type,
                        )
                    //Rolle forsorger benyttes ikke i noe journalføring hendelse...
                    FORSORGER.kode -> PersonIdentier(
                        annenPersonPin,
                        pinItemUtlandList,
                        sedType = sed.type,
                    )
                    //etterlatte benyttes i journalføring hendelse..
                    ETTERLATTE.kode -> PersonIdentier(
                        annenPersonPin,
                        pinItemUtlandList,
                        sedType = sed.type,
                    )
                    else -> null
                }
                return annenPersonRelasjon
            }
        }
        return null
    }

}