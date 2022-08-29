package no.nav.eessi.pensjon.pdl.adresseoppdatering

import no.nav.eessi.pensjon.eux.model.sed.Adresse
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.models.EndringsmeldingKontaktAdresse
import no.nav.eessi.pensjon.models.EndringsmeldingUtenlandskAdresse
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class SedTilPDLAdresse(private val kodeverkClient: KodeverkClient) {

    fun konverter(kilde: String, sedAdresse: Adresse): EndringsmeldingKontaktAdresse {
        return EndringsmeldingKontaktAdresse(
            kilde = kilde,
            gyldigFraOgMed = LocalDate.now(),
            gyldigTilOgMed = LocalDate.now().plusYears(1),
            coAdressenavn = null,
            adresse = EndringsmeldingUtenlandskAdresse(
                adressenavnNummer = sedAdresse.gate,
                bygningEtasjeLeilighet = sedAdresse.bygning,
                bySted = sedAdresse.by,
                landkode = kodeverkClient.finnLandkode(sedAdresse.land!!)!!,
                postboksNummerNavn = null,
                postkode = sedAdresse.postnummer,
                regionDistriktOmraade = sedAdresse.region
            )
        )
    }
}