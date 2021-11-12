package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.R005
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import no.nav.eessi.pensjon.personidentifisering.SEDPersonRelasjon
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer

class R005Relasjon(private val sed: SED, private val bucType: BucType, val rinaDocumentId: String) : AbstractRelasjon(sed, bucType,rinaDocumentId) {
    override fun hentRelasjoner(): List<SEDPersonRelasjon> {
        return filterPinPersonR005(sed as R005)
    }

    private fun filterPinPersonR005(sed: R005): List<SEDPersonRelasjon> {
        return sed.recoveryNav?.brukere
            ?.mapNotNull { bruker ->
                val relasjon = mapRBUC02Relasjon(bruker.tilbakekreving?.status?.type)
                if (relasjon != Relasjon.ANNET) {

                    val fnr = Fodselsnummer.fra(bruker.person?.pin?.firstOrNull { it.land == "NO" }?.identifikator)
                    val pinItemUtlandList = bruker.person?.pin?.filterNot { it.land == "NO" }

                    SEDPersonRelasjon( fnr, pinItemUtlandList, relasjon, sedType = sed.type )

                } else {
                    null
                }
            } ?: emptyList()
    }

    private fun mapRBUC02Relasjon(type: String?): Relasjon {
        return when (type) {
            "enke_eller_enkemann" -> Relasjon.GJENLEVENDE
            "forsikret_person" -> Relasjon.FORSIKRET
            "avdød_mottaker_av_ytelser" -> Relasjon.AVDOD
            else -> Relasjon.ANNET
        }
    }
}
