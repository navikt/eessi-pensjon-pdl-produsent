package no.nav.eessi.pensjon.pdl.filtrering

import no.nav.eessi.pensjon.eux.UtenlandskId
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskIdentifikasjonsnummer
import no.nav.eessi.pensjon.klienter.kodeverk.KodeverkClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component


@Component
class PdlFiltrering(private val kodeverk: KodeverkClient) {

    private val logger = LoggerFactory.getLogger(PdlFiltrering::class.java)


    /**
     * Sjekk om uid i Sed finnes i PDL
     *
     * Konverterer 2 bokstavsutlandkode til trebokstavsutlandskode
     * Sjekker om 3-bokstavslandkode og identifikasjonsnummer fra Sed finnes i PDL
     *
     */
    fun finnesUidFraSedIPDL(
        utenlandskeIdPDL: List<UtenlandskIdentifikasjonsnummer>,
        utenlandskIdSed: UtenlandskId
    ): Boolean {
        utenlandskeIdPDL.forEach { utenlandskIdIPDL ->
            val landkodeFraPdl = kodeverk.finnLandkode(utenlandskIdIPDL.utstederland)
            if (utenlandskIdSed.land == landkodeFraPdl && utenlandskIdSed.id == utenlandskIdIPDL.identifikasjonsnummer) {
                return true
            }
        }
        return false
    }

    /**
     * Sjekk om uid i Sed finnes i PDL
     *
     * Konverterer 2 bokstavsutlandkode til trebokstavsutlandskode
     * Sjekker om 3-bokstavslandkode og identifikasjonsnummer fra Sed finnes i PDL
     *
     */
    fun skalOppgaveOpprettes(utenlandskeIdPDL: List<UtenlandskIdentifikasjonsnummer>, utenlandskIdSed: UtenlandskId): Boolean {
        utenlandskeIdPDL.forEach { utenlandskIdIPDL ->
            val landkodeFraPdl = kodeverk.finnLandkode(utenlandskIdIPDL.utstederland)
            if (utenlandskIdSed.land == landkodeFraPdl && utenlandskIdSed.id != utenlandskIdIPDL.identifikasjonsnummer) {
                return true
            }
        }
        return false
    }

    fun sjekkYterligerePaaPDLuidMotSedUid(utenlandskeIdPDL: List<UtenlandskIdentifikasjonsnummer>, utenlandskIdSed: UtenlandskId): Boolean {
        utenlandskeIdPDL.forEach { utenlandskIdIPDL ->
            val landkodeFraPdl = kodeverk.finnLandkode(utenlandskIdIPDL.utstederland)
            if (utenlandskIdSed.land == landkodeFraPdl && utenlandskIdSed.id != utenlandskIdIPDL.identifikasjonsnummer) {
                return erFaktiskPDLuidLikSedUid(utenlandskIdIPDL.identifikasjonsnummer, utenlandskIdSed.id, utenlandskIdIPDL.utstederland)
            }
        }
        return false
    }

    fun erFaktiskPDLuidLikSedUid(uidPdl: String, uidSed: String, land: String): Boolean {
        //landsesefikk valideringer ut ifra hva som finnes i PDL .. gjelder kun se for nå.
        return when (land) {
            "SWE" -> !sjekkForSverigeIdFraPDL(uidPdl, uidSed)
            else -> true  //betyr oppgave
        }
    }

    //     "19540202123" ---> "540202-2123" 770113-1234
    // returnerer true dersom pdluid faktisk er lik sedui (før oppgave må ivertes)
    fun sjekkForSverigeIdFraPDL(pdlUid: String, sedUid: String) : Boolean {
        val pdluidTrimmedAndReplaced = pdlUid.trim().replace(" ","").removeRange(0,2)
        val seduidReplaced = sedUid.replace("-","")
        logger.debug("validering Oppgave SE: ${(pdluidTrimmedAndReplaced == seduidReplaced)} (true er ikke Oppgave)")
        return pdluidTrimmedAndReplaced == seduidReplaced
    }

}