package no.nav.eessi.pensjon.pdl.adresseoppdatering

import no.nav.eessi.pensjon.eux.model.sed.Adresse
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.models.EndringsmeldingKontaktAdresse
import no.nav.eessi.pensjon.models.EndringsmeldingUtenlandskAdresse
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class SedTilPDLAdresse(private val kodeverkClient: KodeverkClient) {

    private val postBoksInfo = listOf("postboks", "postb", "postbox", "p.b", "po.box")

    fun konverter(kilde: String, sedAdresse: Adresse) =
        EndringsmeldingKontaktAdresse(
            kilde = kilde,
            gyldigFraOgMed = LocalDate.now(),
            gyldigTilOgMed = LocalDate.now().plusYears(1),
            coAdressenavn = null,
            adresse = EndringsmeldingUtenlandskAdresse(
                adressenavnNummer = if (!inneholderPostBoksInfo(sedAdresse.gate)) sedAdresse.gate else null,
                bygningEtasjeLeilighet = sedAdresse.bygning,
                bySted = sedAdresse.by,
                landkode = kodeverkClient.finnLandkode(sedAdresse.land!!)!!,
                postboksNummerNavn = if (inneholderPostBoksInfo(sedAdresse.gate)) sedAdresse.gate else null,
                postkode = sedAdresse.postnummer,
                regionDistriktOmraade = sedAdresse.region
            )
        )

    private fun inneholderPostBoksInfo(gate: String?) = postBoksInfo.any { gate!!.contains(it) }
}