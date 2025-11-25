package no.nav.eessi.pensjon.pdl.adresseoppdatering

import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.buc.BucMetadata
import no.nav.eessi.pensjon.eux.model.buc.Participant
import no.nav.eessi.pensjon.eux.model.sed.Adresse
import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.pdl.*
import no.nav.eessi.pensjon.pdl.validering.erRelevantForEESSIPensjon
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.PersonoppslagException
import no.nav.eessi.pensjon.personoppslag.pdl.model.*
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe.FOLKEREGISTERIDENT
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe.NPID
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class VurderAdresseoppdatering(
    private val personService: PersonService,
    private val euxService: EuxService,
    private val sedTilPDLAdresse: SedTilPDLAdresse
) : OppgaveModel() {
    private val logger = LoggerFactory.getLogger(VurderAdresseoppdatering::class.java)
    private val secureLogger = LoggerFactory.getLogger("secureLog")

    fun vurderUtenlandskKontaktadresse(sedHendelse: SedHendelse): Result {
        require(erRelevantForEESSIPensjon(sedHendelse)) { return IngenOppdatering("SED ikke relevant for EESSI Pensjon, buc: ${sedHendelse.bucType}, sed: ${sedHendelse.sedType}, sektor: ${sedHendelse.sektorKode}", "SED ikke relevant for EESSI Pensjon") }

        val sed = euxService.hentSed(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId).also { secureLogger.info("SED:\n$it") }

        val avsender = if (sedHendelse.avsenderLand != null) {
            Pair(sedHendelse.avsenderLand, sedHendelse.avsenderNavn)
        } else {
            val bucMetadata = euxService.getBucMetadata(sedHendelse.rinaSakId)
            populerSenderland(bucMetadata!!, sedHendelse.rinaDokumentId)
        }

        require(adresseErIUtlandet(adresseFra(sed))) { return IngenOppdatering("Bruker har ikke utenlandsk adresse i SED") }
        require(avsenderISedHendelse(avsender.first, avsender.second )) { "Mangler avsenderNavn eller avsenderLand i sedHendelse - avslutter adresseoppdatering: $sedHendelse" }
        require(avsenderLandOgAdressensLandErSamme(sedHendelse.avsenderId, avsender.first, sed)) {
            return IngenOppdatering(
                "Adressens landkode (${adresseFra(sed)?.land}) er ulik landkode på avsenderland (${sedHendelse.avsenderLand})",
                "Adressens landkode er ulik landkode på avsenderland")
        }

        // Når det ikke finnes norsk fnr eller npid så er det helt fint at dette tas av Id & Fordeling
        require (hasNorskPinOrNpid(brukerFra(sed))) { return IngenOppdatering("Bruker har ikke norsk pin eller npid i SED") }

        val npid = Fodselsnummer.fra(norskPinEllerNpid(brukerFra(sed))?.identifikator)?.erNpid == true
        if(npid) logger.info("Fodselsnummer er npid")
        else logger.info("Fodselsnummer er norsk ident")

        val personFraPDL = try {

            val normalisertNorskPIN = try {
                if (npid) {
                    norskPinEllerNpid(brukerFra(sed))
                }
                normaliserNorskPin(norskPinEllerNpid(brukerFra(sed))!!.identifikator!!)
            } catch (ex: IllegalArgumentException) {
                return IngenOppdatering(
                    "Brukers norske id fra SED validerer ikke: \"${norskPinEllerNpid(brukerFra(sed))!!.identifikator!!}\" - ${ex.message}",
                    "Brukers norske id fra SED validerer ikke",
                )
            }
            val ident = if (!npid) NorskIdent(normalisertNorskPIN)
                else Npid(norskPinEllerNpid(brukerFra(sed))?.identifikator!!)

            personService.hentPerson(ident) ?: throw NullPointerException("hentPerson returnerte null")

        } catch (ex: PersonoppslagException) {
            if (ex.code == "not_found") {
                return IngenOppdatering("Finner ikke bruker i PDL med angitt fnr i SED")
            }
            throw ex
        }.also { secureLogger.info("Person fra PDL:\n${it.toJson()}") }

        val norskFnrEllerNpid =
            personFraPDL.identer
                .firstOrNull { it.gruppe == FOLKEREGISTERIDENT || it.gruppe == NPID }?.ident
                ?: personFraPDL.identer.first().ident

        require(erUtenAdressebeskyttelse(personFraPDL.adressebeskyttelse)) { return IngenOppdatering("Ingen adresseoppdatering") }
        require(AdresseValidering.erNorskAdresse(personFraPDL.bostedsadresse?.metadata)) { return IngenOppdatering("Ingen adresseoppdatering da dette allerede har en norsk adresse") }

        logger.info("Vi har funnet en person fra PDL med samme norsk identifikator som bruker i SED")

        val utenlandskKontaktadresseRegistrertAvNAV =
            personFraPDL.kontaktadresse?.let { if (it.metadata.master.uppercase().equals("FREG")) null else it  }?.utenlandskAdresse

        if (sedTilPDLAdresse.isUtenlandskAdresseISEDMatchMedAdresseIPDL(adresseFra(sed)!!, utenlandskKontaktadresseRegistrertAvNAV)) {
            require(LocalDate.now() != personFraPDL.kontaktadresse!!.gyldigFraOgMed?.toLocalDate()) {
                return IngenOppdatering("Adresse finnes allerede i PDL med dagens dato som gyldig-fra-dato, dropper oppdatering")
            }
            return Oppdatering(
                "Adressen fra ${sedHendelse.avsenderLand} finnes allerede i PDL, oppdaterer gyldig til og fra dato",
                korrigerDatoEndringOpplysning(
                    norskFnr = norskFnrEllerNpid,
                    kilde = sedHendelse.avsenderNavn + " (" + sedHendelse.avsenderLand + ")",
                    kontaktadresse = personFraPDL.kontaktadresse!!
                )
            , metricTagValueOverride = "Adressen finnes allerede i PDL, oppdaterer gyldig til og fra dato"
            )
        }

        val endringsmelding = try {
            sedTilPDLAdresse.konverter(sedHendelse.avsenderNavn + " (" + sedHendelse.avsenderLand + ")", adresseFra(sed)!!)
        } catch (ex: IllegalArgumentException) {
            return IngenOppdatering(
                "Adressen fra ${sedHendelse.avsenderLand} validerer ikke etter reglene til PDL: ${ex.message}",
                "Adressen validerer ikke etter reglene til PDL"
            )
        }

        return Oppdatering(
            "Adressen i SED fra ${sedHendelse.avsenderLand} finnes ikke i PDL, sender OPPRETT endringsmelding",
            opprettAdresseEndringOpplysning(norskFnrEllerNpid, endringsmelding),
            metricTagValueOverride = "Adressen i SED finnes ikke i PDL, sender OPPRETT endringsmelding"
        )
    }

    private fun opprettAdresseEndringOpplysning(ident: String, endringsmelding: EndringsmeldingKontaktAdresse) =
        PdlEndringOpplysning(
            listOf(
                Personopplysninger(
                    endringstype = Endringstype.OPPRETT,
                    ident = ident,
                    endringsmelding = endringsmelding,
                    opplysningstype = Opplysningstype.KONTAKTADRESSE,
                )
            )
        )

    private fun normaliserNorskPin(norskPinFraSED: String) =
        Fodselsnummer.fraMedValidation(norskPinFraSED)!!.value
            .also {
                if (it != norskPinFraSED) {
                    logger.info("Fnr i SED på ustandard format - alt utenom tall fjernet")
                }
            }

    private fun hasNorskPinOrNpid(bruker: Bruker?) = norskPinEllerNpid(bruker) != null

    private fun norskPinEllerNpid(bruker: Bruker?) =
        bruker?.person?.pin?.firstOrNull { it.land == "NO" }

    private fun avsenderLandOgAdressensLandErSamme(avsenderId: String?, avsenderLand: String?, sed: SED) =
        avsenderLand == adresseFra(sed)?.land || isSedHendelseFromPreprod(avsenderId) /* "alt" er fra Norge i preprod */

    private fun adresseFra(sed: SED) = brukerFra(sed)?.adresse

    private fun brukerFra(sed: SED) = sed.nav?.bruker

    private fun isSedHendelseFromPreprod(avsenderId: String?) =
        avsenderId in listOf("NO:NAVAT05", "NO:NAVAT07")

    private fun avsenderISedHendelse(avsenderLand: String?, avsenderId: String?) =
        avsenderLand != null && avsenderId != null

    private fun adresseErIUtlandet(adresse: Adresse?) =
        adresse?.land != null && adresse.land != "NO"

    fun korrigerDatoEndringOpplysning(norskFnr: String, kilde: String, kontaktadresse: Kontaktadresse) =
        PdlEndringOpplysning(listOf(
            Personopplysninger(
                endringstype = Endringstype.KORRIGER,
                ident = norskFnr,
                endringsmelding = EndringsmeldingKontaktAdresse(
                    kilde = formaterVekkHakeParentes(kilde),
                    gyldigFraOgMed = LocalDate.now(),
                    gyldigTilOgMed = LocalDate.now().plus(SedTilPDLAdresse.gyldighetsperiodeKontaktadresse),
                    coAdressenavn = kontaktadresse.coAdressenavn,
                    adresse = EndringsmeldingUtenlandskAdresse(
                        adressenavnNummer = kontaktadresse.utenlandskAdresse!!.adressenavnNummer,
                        bygningEtasjeLeilighet = kontaktadresse.utenlandskAdresse!!.bygningEtasjeLeilighet,
                        bySted = kontaktadresse.utenlandskAdresse!!.bySted,
                        landkode = kontaktadresse.utenlandskAdresse!!.landkode,
                        postboksNummerNavn = kontaktadresse.utenlandskAdresse!!.postboksNummerNavn,
                        postkode = kontaktadresse.utenlandskAdresse!!.postkode,
                        regionDistriktOmraade = kontaktadresse.utenlandskAdresse!!.regionDistriktOmraade
                    )
                ),
                opplysningstype = Opplysningstype.KONTAKTADRESSE,
                opplysningsId = kontaktadresse.metadata.opplysningsId
            )
        ))

    private fun populerSenderland(bucMetadata: BucMetadata, dokumentId: String): Pair<String?, String?> {
        val participant : Participant? = bucMetadata.documents
            .filter { it.id == dokumentId }
            .flatMap { it.conversations }
            .flatMap { it.participants.orEmpty() }
            .filter { it.role == "Sender" }
            .firstOrNull()
        return Pair(participant?.organisation?.countryCode, participant?.organisation?.id).also { logger.info("Henter avsenderland: $it fra metadata") }
    }

    fun formaterVekkHakeParentes(kilde: String): String =
        kilde.replace("->", "")
            .replace("<-", "")
            .replace("<", "")
            .replace(">", "")

}