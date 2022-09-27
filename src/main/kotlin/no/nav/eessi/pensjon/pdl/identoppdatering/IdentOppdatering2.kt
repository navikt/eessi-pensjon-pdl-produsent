package no.nav.eessi.pensjon.pdl.identoppdatering

import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.UtenlandskId
import no.nav.eessi.pensjon.eux.UtenlandskPersonIdentifisering
import no.nav.eessi.pensjon.eux.model.sed.Adresse
import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.models.EndringsmeldingUID
import no.nav.eessi.pensjon.models.PdlEndringOpplysning
import no.nav.eessi.pensjon.models.Personopplysninger
import no.nav.eessi.pensjon.models.SedHendelse
import no.nav.eessi.pensjon.oppgave.OppgaveHandler
import no.nav.eessi.pensjon.pdl.filtrering.PdlFiltrering
import no.nav.eessi.pensjon.pdl.validering.PdlValidering
import no.nav.eessi.pensjon.pdl.validering.erRelevantForEESSIPensjon
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.PersonoppslagException
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endringstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.Opplysningstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Person
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service


@Service
class IdentOppdatering2 (
    private val euxService: EuxService,
    private val pdlFiltrering: PdlFiltrering,
    private val pdlValidering: PdlValidering,
    private val oppgaveHandler: OppgaveHandler,
    private val kodeverkClient: KodeverkClient,
    private val personService: PersonService,
    private val utenlandskPersonIdentifisering: UtenlandskPersonIdentifisering
) {

    private val logger = LoggerFactory.getLogger(IdentOppdatering2::class.java)
    private val secureLogger = LoggerFactory.getLogger("secureLog")

    fun oppdaterUtenlandskIdent(sedHendelse: SedHendelse): Result {
        require(erRelevantForEESSIPensjon(sedHendelse)) { return NoUpdate("Ikke relevant for eessipensjon") }

        require(sedHendelse.avsenderLand.isNullOrEmpty().not()) {
            return NoUpdate("Avsenderland mangler eller avsenderland er ikke det samme som uidland")
        }

        require(sedHendelse.avsenderNavn.isNullOrEmpty().not()) {
            return NoUpdate("AvsenderNavn er ikke satt, kan derfor ikke lage endringsmelding")
        }

        val sed = euxService.hentSed(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)
            .also { secureLogger.debug("SED:\n$it") }

        require(sed.nav?.bruker?.person?.pin?.filter { it.land == "NO" }.isNullOrEmpty().not()) {
            return NoUpdate("Bruker har ikke norsk pin i SED")
        }

        //NoUpdate der avsenderland er ulik UIDland
        require(sedHendelse.avsenderLand == sed.nav?.bruker?.person?.pin?.firstOrNull()?.land) {
            return NoUpdate("Avsenderland mangler eller avsenderland er ikke det samme som uidland")
        }

        val utenlandskPin = harUtenlandskPin(sed)
        require(utenlandskPin != null) { return NoUpdate("Bruker har ikke utenlandsk ident") }

        val normalisertNorskPIN = hentNorskPin(sed) as String
        val personFraPDL = hentPdlPerson(normalisertNorskPIN) as Person

        //NoUpdate der UID er fra en avdød person
        require(personFraPDL.erDoed().not()) {
            return NoUpdate("Identifisert person registrert med doedsfall")
        }

        //NoUpdate der UID ikke validerer

        //NoUpdate der UID er lik UID fra PDL

        //Opprette oppgave der er en eller flere nye utenlandske identer

        //Opprette oppgave der UID ikke finnes i PDL, men det finnes allerede en UID i PDL som er ulik

        //Update (endringsmelding) der UID ikke finnes i PDL

        //Update (endringsmelding) der UID (med feil format) ikke finnes i PDL, så skal den konverteres til korrekt format før sending


        return Update(
            "Innsending av endringsmelding",
            pdlEndringOpplysning(personFraPDL.identer.firstOrNull()?.ident!!, UtenlandskId(id ="dummy", land = "PL" ), sedHendelse.avsenderNavn!!)
        )


    }

    private fun hentNorskPin(sed: SED): Any {
        return try {
            normaliserNorskPin(norskPin(brukerFra(sed))!!.identifikator!!)
        } catch (ex: IllegalArgumentException) {
            return NoUpdate(
                "Brukers norske id fra SED validerer ikke: \"${norskPin(brukerFra(sed))!!.identifikator!!}\" - ${ex.message}"
            )
        }
    }

    private fun hentPdlPerson(normalisertNorskPIN: String): Any {
        return try {
            personService.hentPerson(NorskIdent(normalisertNorskPIN))
                ?: throw NullPointerException("hentPerson returnerte null")
        } catch (ex: PersonoppslagException) {
            if (ex.code == "not_found") {
                return NoUpdate("Finner ikke bruker i PDL med angitt fnr i SED")
            }
            throw ex
        }.also { secureLogger.debug("Person fra PDL:\n${it.toJson()}") }
    }

    private fun harUtenlandskPin(sed: SED): String? = sed.nav?.bruker?.person?.pin?.firstOrNull { it.land != "NO" }?.identifikator

    private fun normaliserNorskPin(norskPinFraSED: String) =
        Fodselsnummer.fraMedValidation(norskPinFraSED)!!.value
            .also {
                if (it != norskPinFraSED) {
                    logger.info("Fnr i SED på ustandard format - alt utenom tall fjernet")
                }
            }

    private fun hasNorskPin(bruker: Bruker?) = norskPin(bruker) != null

    private fun norskPin(bruker: Bruker?) =
        bruker?.person?.pin?.firstOrNull { it.land == "NO" }

    private fun avsenderLandOgAdressensLandErSamme(sedHendelse: SedHendelse, sed: SED) =
        sedHendelse.avsenderLand == adresseFra(sed)?.land || isSedHendelseFromPreprod(sedHendelse) /* "alt" er fra Norge i preprod */

    private fun adresseFra(sed: SED) = brukerFra(sed)?.adresse

    private fun brukerFra(sed: SED) = sed.nav?.bruker

    private fun isSedHendelseFromPreprod(sedHendelse: SedHendelse) =
        sedHendelse.avsenderId in listOf("NO:NAVAT05", "NO:NAVAT07")

    private fun avsenderISedHendelse(sedHendelse: SedHendelse) = sedHendelse.avsenderNavn != null && sedHendelse.avsenderLand != null

    private fun adresseErIUtlandet(adresse: Adresse?) = adresse?.land != null && adresse.land != "NO"

    private fun pdlEndringOpplysning(norskFnr: String, utenlandskPin: UtenlandskId, kilde: String) =
        PdlEndringOpplysning(
            listOf(
                Personopplysninger(
                    endringstype = Endringstype.OPPRETT,
                    ident = norskFnr,
                    endringsmelding = EndringsmeldingUID(
                        identifikasjonsnummer = konvertertTilPdlFormat(utenlandskPin),
                        utstederland = kodeverkClient.finnLandkode(utenlandskPin.land)
                            ?: throw RuntimeException("Feil ved landkode"),
                        kilde = kilde
                    ),
                    opplysningstype = Opplysningstype.UTENLANDSKIDENTIFIKASJONSNUMMER
                )
            )
        )

    private fun konvertertTilPdlFormat(utenlandskPin: UtenlandskId): String {
        val uid = utenlandskPin.id
        if (utenlandskPin.land == "SE") {
            if (uid.length == 10) uid.replaceRange(5, 5, "-")
            if (uid.length == 12) uid.replaceRange(7, 7, "-")
        }
        return uid
    }

    sealed class Result {
        abstract val description: String
    }

    data class Update(override val description: String, val identOpplysninger: PdlEndringOpplysning) : Result()
    data class NoUpdate(override val description: String) : Result()
}