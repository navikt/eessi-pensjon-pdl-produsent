package no.nav.eessi.pensjon.pdl.adresseoppdatering

import no.nav.eessi.pensjon.eux.model.sed.Adresse
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.kodeverk.LandkodeException
import no.nav.eessi.pensjon.models.EndringsmeldingKontaktAdresse
import no.nav.eessi.pensjon.models.EndringsmeldingUtenlandskAdresse
import no.nav.eessi.pensjon.pdl.adresseoppdatering.AdresseValidering.erGyldigAdressenavnNummerEllerBygningEtg
import no.nav.eessi.pensjon.pdl.adresseoppdatering.AdresseValidering.erGyldigByStedEllerRegion
import no.nav.eessi.pensjon.pdl.adresseoppdatering.AdresseValidering.erGyldigPostKode
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskAdresse
import org.springframework.stereotype.Component
import java.time.LocalDate
import kotlin.contracts.contract

@Component
class SedTilPDLAdresse(private val kodeverkClient: KodeverkClient) {

    private val postBoksInfo = listOf("postboks", "postb", "postbox", "p.b", "po.box")

    fun konverter(kilde: String, sedAdresse: Adresse): Result {
        val land = sedAdresse.land ?: return Valideringsfeil("Mangler landkode")

        val landkode = try {
            kodeverkClient.finnLandkode(land)
        } catch (ex: LandkodeException) {
            return  Valideringsfeil("Ugyldig landkode: $land")
        }

        val adressenavnNummer = sedAdresse.gate
            ?.let { if(inneholderPostBoksInfo(it)) null else it }
            ?.also { require (erGyldigAdressenavnNummerEllerBygningEtg(it)) {
                return Valideringsfeil("Ikke gyldig adressenavnNummer: $it") }
            }

        val bySted = sedAdresse.by
            ?.also { require(erGyldigByStedEllerRegion(it)) {
                return Valideringsfeil("Ikke gyldig bySted: $it") }
            }

        val regionDistriktOmraade = sedAdresse.region
            ?.also { require(erGyldigByStedEllerRegion(it)) {
                return Valideringsfeil("Ikke gyldig regionDistriktOmraade: $it") }
            }

        val postkode = sedAdresse.postnummer
            ?.also { require(erGyldigPostKode(it)) {
                return Valideringsfeil("Ikke gyldig postkode: $it") }
            }

        val bygningEtasjeLeilighet = sedAdresse.bygning
            ?.also { require(erGyldigAdressenavnNummerEllerBygningEtg(it)) {
                return Valideringsfeil("Ikke gyldig bygningEtasjeLeilighet: $it") }
            }

        val postboksNummerNavn = sedAdresse.gate
            ?.let { if(inneholderPostBoksInfo(it)) it else null }

        require (listOf(adressenavnNummer, bygningEtasjeLeilighet, bySted, postboksNummerNavn, postkode, regionDistriktOmraade).any { it.isNullOrEmpty().not() }) {
            return Valideringsfeil("Ikke gyldig adresse, har kun landkode")
        }

        return OK(
            EndringsmeldingKontaktAdresse(
                kilde = kilde,
                gyldigFraOgMed = LocalDate.now(),
                gyldigTilOgMed = LocalDate.now().plusYears(1),
                coAdressenavn = null,
                adresse = EndringsmeldingUtenlandskAdresse(
                    adressenavnNummer = adressenavnNummer,
                    bygningEtasjeLeilighet = bygningEtasjeLeilighet,
                    bySted = bySted,
                    landkode = landkode!!,
                    postboksNummerNavn = postboksNummerNavn,
                    postkode = postkode,
                    regionDistriktOmraade = regionDistriktOmraade
                )
            )
        )

    }

    @SuppressWarnings("kotlin:S1067") // enkel struktur gir lav kognitiv load
    fun isUtenlandskAdresseISEDMatchMedAdresseIPDL(sedAdresse: Adresse, pdlAdresse: UtenlandskAdresse) =
        sedAdresse.gate in listOf(pdlAdresse.adressenavnNummer, pdlAdresse.postboksNummerNavn)
                && sedAdresse.bygning == pdlAdresse.bygningEtasjeLeilighet
                && sedAdresse.by == pdlAdresse.bySted
                && sedAdresse.postnummer == pdlAdresse.postkode
                && sedAdresse.region == pdlAdresse.regionDistriktOmraade
                && sedAdresse.land == pdlAdresse.landkode.let { kodeverkClient.finnLandkode(it) }

    private fun inneholderPostBoksInfo(gate: String) = postBoksInfo.any { gate.contains(it) }

    sealed class Result
    data class OK(val endringsmeldingKontaktAdresse: EndringsmeldingKontaktAdresse): Result()
    data class Valideringsfeil(val description: String): Result()
}