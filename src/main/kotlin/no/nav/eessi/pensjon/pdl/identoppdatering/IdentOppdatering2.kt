package no.nav.eessi.pensjon.pdl.identoppdatering

import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.UtenlandskId
import no.nav.eessi.pensjon.eux.UtenlandskPersonIdentifisering
import no.nav.eessi.pensjon.eux.model.sed.Adresse
import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.PinItem
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.models.EndringsmeldingUID
import no.nav.eessi.pensjon.models.PdlEndringOpplysning
import no.nav.eessi.pensjon.models.Personopplysninger
import no.nav.eessi.pensjon.models.SedHendelse
import no.nav.eessi.pensjon.oppgave.OppgaveHandler
import no.nav.eessi.pensjon.pdl.validering.PdlValidering
import no.nav.eessi.pensjon.pdl.validering.erRelevantForEESSIPensjon
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.PersonoppslagException
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endringstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.Opplysningstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Person
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskIdentifikasjonsnummer
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service


@Service
class IdentOppdatering2 (
    private val euxService: EuxService,
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
        val pinItem = sed.nav?.bruker?.person?.pin?.firstOrNull()
        require(sedHendelse.avsenderLand == pinItem?.land) {
            return NoUpdate("Avsenderland mangler eller avsenderland er ikke det samme som uidland")
        }

        val utenlandskPin = harUtenlandskPin(sed)
        require(utenlandskPin != null) { return NoUpdate("Bruker har ikke utenlandsk ident") }


        //NoUpdate der UID ikke validerer
        require(pdlValidering.erPersonValidertPaaLand(utenlandskPin.identifikator!!, utenlandskPin.land!!)) {
            return NoUpdate("Ingen validerte identifiserte personer funnet")
        }

        val normalisertNorskPIN = hentNorskPin(sed) as String
        val personFraPDL = hentPdlPerson(normalisertNorskPIN) as Person

        //NoUpdate der UID er lik UID fra PDL
        val utenlandskPinFraPDL = UtenlandskId(utenlandskPin.identifikator!!, utenlandskPin.land!!)
        require(!finnesUidFraSedIPDL(personFraPDL.utenlandskIdentifikasjonsnummer, utenlandskPinFraPDL)) {
            return NoUpdate("PDL uid er identisk med SED uid")
        }

        //Opprette oppgave der er en eller flere nye utenlandske identer
        //Opprette oppgave der UID ikke finnes i PDL, men det finnes allerede en UID i PDL som er ulik

        if (skalOppgaveOpprettes(personFraPDL.utenlandskIdentifikasjonsnummer, utenlandskPinFraPDL)) {
            //ytterligere sjekk om f.eks SWE fnr i PDL faktisk er identisk med sedUID (hvis så ikke opprett oppgave bare avslutt)
            require(sedUidErUlikPDLUid(personFraPDL.utenlandskIdentifikasjonsnummer,
                utenlandskPinFraPDL
            )) {
                // Oppretter ikke oppgave, Det som finnes i PDL er faktisk likt det som finnes i SED, avslutter
                return NoUpdate("PDLuid er identisk med SEDuid")
            }
            val identifisetPerson = identifisertPerson(normalisertNorskPIN, personFraPDL)

            return if (oppgaveHandler.opprettOppgaveForUid(sedHendelse, utenlandskPinFraPDL, identifisetPerson)) {
                NoUpdate("Det finnes allerede en annen uid fra samme land (Oppgave)")
            } else {
                NoUpdate("Oppgave opprettet tidligere")
            }
        }

        //Update (endringsmelding) der UID (med feil format) ikke finnes i PDL, så skal den konverteres til korrekt format før sending


        //Update (endringsmelding) der UID ikke finnes i PDL

        logger.info("Oppdaterer PDL med Ny Utenlandsk Ident fra ${sedHendelse.avsenderNavn}")
        return Update(
            "Innsending av endringsmelding",
            pdlEndringOpplysning(personFraPDL.identer.firstOrNull()?.ident!!, utenlandskPinFraPDL, sedHendelse.avsenderNavn!!),
        )

    }

    private fun identifisertPerson(
        normalisertNorskPIN: String,
        personFraPDL: Person
    ) = IdentifisertPerson(
        fnr = Fodselsnummer.fra(normalisertNorskPIN),
        uidFraPdl = personFraPDL.utenlandskIdentifikasjonsnummer,
        aktoerId = personFraPDL.identer.first { it.gruppe == IdentGruppe.AKTORID }.ident,
        landkode = personFraPDL.landkode(),
        geografiskTilknytning = personFraPDL.geografiskTilknytning?.gtKommune
            ?: personFraPDL.geografiskTilknytning?.gtBydel,
        harAdressebeskyttelse = false,
        personListe = null,
        personRelasjon = null,
        erDoed = personFraPDL.erDoed(),
        kontaktAdresse = personFraPDL.kontaktadresse
    )

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

    private fun harUtenlandskPin(sed: SED): PinItem? = sed.nav?.bruker?.person?.pin?.firstOrNull { it.land != "NO" }

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

    fun skalOppgaveOpprettes(utenlandskeIdPDL: List<UtenlandskIdentifikasjonsnummer>, utenlandskIdSed: UtenlandskId): Boolean {
        return uidLandLiktPdlLand(utenlandskeIdPDL, utenlandskIdSed) && utenlandsIdUlikPdlId(utenlandskeIdPDL, utenlandskIdSed)
    }

    /**
     * Sjekk om uid i Sed finnes i PDL
     *
     * Konverterer 2 bokstavsutlandkode til trebokstavsutlandskode
     * Sjekker om 3-bokstavslandkode og identifikasjonsnummer fra Sed finnes i PDL
     *
     */
    fun uidLandLiktPdlLand(utenlandskeIdPDL: List<UtenlandskIdentifikasjonsnummer>, utenlandskIdSed: UtenlandskId): Boolean {
        utenlandskeIdPDL.forEach { utenlandskIdIPDL ->
            val landkodeFraPdl = kodeverkClient.finnLandkode(utenlandskIdIPDL.utstederland)
            if (utenlandskIdSed.land == landkodeFraPdl) return true
        }
        return false
    }

    fun utenlandsIdUlikPdlId(utenlandskeIdPDL: List<UtenlandskIdentifikasjonsnummer>, utenlandskIdSed: UtenlandskId): Boolean {
        utenlandskeIdPDL.forEach { utenlandskIdIPDL ->
            if ( utenlandskIdSed.id != utenlandskIdIPDL.identifikasjonsnummer) return true
        }
        return false
    }

    fun finnesUidFraSedIPDL(utenlandskeIdPDL: List<UtenlandskIdentifikasjonsnummer>, utenlandskIdSed: UtenlandskId): Boolean {
        return uidLandLiktPdlLand(utenlandskeIdPDL, utenlandskIdSed) && !utenlandsIdUlikPdlId(utenlandskeIdPDL, utenlandskIdSed)
    }

    fun sedUidErUlikPDLUid(utenlandskeIdPDL: List<UtenlandskIdentifikasjonsnummer>, utenlandskIdSed: UtenlandskId): Boolean {
        utenlandskeIdPDL.forEach { utenlandskIdIPDL ->
            val landkodeFraPdl = kodeverkClient.finnLandkode(utenlandskIdIPDL.utstederland)
            if (utenlandskIdSed.land == landkodeFraPdl && utenlandskIdSed.id != utenlandskIdIPDL.identifikasjonsnummer) {
                if (utenlandskIdIPDL.utstederland != "SWE") return true

                val pdluidTrimmedAndReplaced = utenlandskIdIPDL.identifikasjonsnummer.trim().replace(" ", "").removeRange(0, 2)
                val seduidReplaced = utenlandskIdSed.id.replace("-", "")
                logger.debug("validering Oppgave SE: ${(pdluidTrimmedAndReplaced == seduidReplaced)} (true er ikke Oppgave)")
                return pdluidTrimmedAndReplaced != seduidReplaced
                //betyr oppgave
                //landsesefikk valideringer ut ifra hva som finnes i PDL .. gjelder kun se for nå.
            }
        }
        return false
    }


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