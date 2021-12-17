package no.nav.eessi.pensjon.pdl.filtrering

import no.nav.eessi.pensjon.eux.UtenlandskId
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskIdentifikasjonsnummer
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient
import org.springframework.stereotype.Component


@Component
class PdlFiltrering(private val kodeverk: KodeverkClient) {

    /**
     * Sjekk om uid i Sed finnes i PDL
     *
     * Konverterer 2 bokstavsutlandkode til trebokstavsutlandskode
     * Sjekker om 3-bokstavslandkode og identifikasjonsnummer fra Sed finnes i PDL
     *
     */
    fun finnesUidFraSedIPDL(
        utenlandskIdPDL: List<UtenlandskIdentifikasjonsnummer>,
        utenlandskIdSed: UtenlandskId
    ): Boolean {

        utenlandskIdPDL.forEach { utenlandskId ->
            val landkodeFraPdl = kodeverk.finnLandkode(utenlandskId.utstederland)
            if (utenlandskIdSed.land == landkodeFraPdl && utenlandskIdSed.id == utenlandskId.identifikasjonsnummer) {
                return true
            }
        }
        return false
    }
}
