package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.R005
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.personidentifisering.PersonIdenter
import no.nav.eessi.pensjon.personidentifisering.UtenlandskPin
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer

class R005Ident() : AbstractIdent() {

    override fun hentRelasjoner(sed: SED): List<PersonIdenter> {
        return filterPinPersonR005(sed as R005)
    }

    private fun filterPinPersonR005(sed: R005): List<PersonIdenter> {
        return sed.recoveryNav?.brukere
            ?.mapNotNull { bruker ->
                if (mapRBUC02Relasjon(bruker.tilbakekreving?.status?.type)) {

                    val fnr = Fodselsnummer.fra(bruker.person?.pin?.firstOrNull { it.land == "NO" }?.identifikator)
                    val pinItemUtlandList = bruker.person?.pin?.filterNot { it.land == "NO" }
                        ?.filter { it.land != null && it.identifikator != null && it.institusjonsnavn != null }
                        ?.map { UtenlandskPin(it.institusjonsnavn!!, it.identifikator!!, it.land!!) }
                    PersonIdenter(fnr, pinItemUtlandList, sedType = sed.type)

                } else {
                    null
                }
            } ?: emptyList()
    }

    private fun mapRBUC02Relasjon(type: String?): Boolean {
        return when (type) {
            "enke_eller_enkemann" -> true
            "forsikret_person" -> true
            "avdød_mottaker_av_ytelser" -> true
            else -> false
        }
    }
}