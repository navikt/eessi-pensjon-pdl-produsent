package no.nav.eessi.pensjon.pdl.identoppdatering

import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.UtenlandskId
import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.models.EndringsmeldingUID
import no.nav.eessi.pensjon.models.PdlEndringOpplysning
import no.nav.eessi.pensjon.models.Personopplysninger
import no.nav.eessi.pensjon.models.SedHendelse
import no.nav.eessi.pensjon.oppgave.OppgaveHandler
import no.nav.eessi.pensjon.pdl.validering.LandspesifikkValidering
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
    private val oppgaveHandler: OppgaveHandler,
    private val kodeverkClient: KodeverkClient,
    private val personService: PersonService,
    private val landspesifikkValidering: LandspesifikkValidering
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

        val pinItem = sed.nav?.bruker?.person?.pin?.firstOrNull()
        require(sedHendelse.avsenderLand == pinItem?.land) {
            return NoUpdate("Avsenderland mangler eller avsenderland er ikke det samme som uidland")
        }

        val utenlandskPinItemFraSed = harUtenlandskPin(sed)
        require(utenlandskPinItemFraSed != null) { return NoUpdate("Bruker har ikke utenlandsk ident") }

        require(utenlandskPinItemFraSed.erPersonValidertPaaLand()) {
            return NoUpdate("Ingen validerte identifiserte personer funnet")
        }

        val normalisertNorskPINFraSed = hentNorskPin(sed) as String
        val personFraPDL = hentPdlPerson(normalisertNorskPINFraSed) as Person

        val utenlandskPinFraSed = UtenlandskId(utenlandskPinItemFraSed.id, utenlandskPinItemFraSed.land)

        require(!utenlandskPinFinnesIPdl(utenlandskPinFraSed, personFraPDL.utenlandskIdentifikasjonsnummer)) {
            return NoUpdate("PDL uid er identisk med SED uid")
        }

        //Opprette oppgave der UID er fra samme land, men ikke finnes i PDL eller der det er er en eller flere nye utenlandske identer
        if (personFraPDL.utenlandskIdentifikasjonsnummer.map { it.utstederland }.contains(kodeverkClient.finnLandkode(utenlandskPinFraSed.land))) {

            return opprettOppgave(personFraPDL, utenlandskPinFraSed, sedHendelse, normalisertNorskPINFraSed)
        }

        logger.info("Oppdaterer PDL med Ny Utenlandsk Ident fra ${sedHendelse.avsenderNavn}")
        return Update(
            //Update (endringsmelding) der UID ikke finnes i PDL
            "Innsending av endringsmelding",
            pdlEndringOpplysning(personFraPDL.identer.firstOrNull()?.ident!!, utenlandskPinFraSed, sedHendelse.avsenderNavn!!),
        )

    }

    private fun opprettOppgave(personFraPDL: Person, utenlandskPinFraSed: UtenlandskId, sedHendelse: SedHendelse, normalisertNorskPINFraSed: String): Result {
        require(!erUidSvenskOgLikPdlUid(personFraPDL.utenlandskIdentifikasjonsnummer.filter { it.utstederland == "SWE" }, utenlandskPinFraSed)) {
            return NoUpdate("Sed er fra Sverige, men uid er lik PDL uid")
        }

        return if (oppgaveHandler.opprettOppgaveForUid(sedHendelse, utenlandskPinFraSed, identifisertPerson(normalisertNorskPINFraSed, personFraPDL))) {
            NoUpdate("Det finnes allerede en annen uid fra samme land (Oppgave)")
        } else NoUpdate("Oppgave opprettet tidligere")
    }

    private fun utenlandskPinFinnesIPdl(utenlandskPin: UtenlandskId, utenlandskeUids: List<UtenlandskIdentifikasjonsnummer>) =
        utenlandskeUids.map { Pair(it.identifikasjonsnummer, kodeverkClient.finnLandkode(it.utstederland)) }.contains(Pair(utenlandskPin.id, utenlandskPin.land))

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

    private fun harUtenlandskPin(sed: SED): UtenlandskId? {
        val pinitem  = sed.nav?.bruker?.person?.pin?.firstOrNull { it.land != "NO" }
        if(pinitem?.land.isNullOrEmpty() || pinitem?.identifikator.isNullOrEmpty()) return null

        return UtenlandskId(pinitem?.identifikator!!, pinitem.land!!)
    }

    private fun normaliserNorskPin(norskPinFraSED: String) =
        Fodselsnummer.fraMedValidation(norskPinFraSED)!!.value
            .also {
                if (it != norskPinFraSED) {
                    logger.info("Fnr i SED på ustandard format - alt utenom tall fjernet")
                }
            }

    private fun norskPin(bruker: Bruker?) =
        bruker?.person?.pin?.firstOrNull { it.land == "NO" }

    private fun brukerFra(sed: SED) = sed.nav?.bruker

    /**
     * Sjekk om uid i Sed finnes i PDL
     *
     * Konverterer 2 bokstavsutlandkode til trebokstavsutlandskode
     * Sjekker om 3-bokstavslandkode og identifikasjonsnummer fra Sed finnes i PDL
     *
     */
    fun erUidSvenskOgLikPdlUid(utenlandskeIdPDL: List<UtenlandskIdentifikasjonsnummer>, utenlandskIdSed: UtenlandskId): Boolean {
        utenlandskeIdPDL.forEach { utenlandskIdIPDL ->
            return landspesifikkValidering.formaterSvenskUID(utenlandskIdIPDL.identifikasjonsnummer) ==
                    landspesifikkValidering.formaterSvenskUID(utenlandskIdSed.id)
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
        if (utenlandskPin.land == "SE") {
            return landspesifikkValidering.formaterSvenskUID(utenlandskPin.id)
        }
        return utenlandskPin.id
    }

    sealed class Result {
        abstract val description: String
    }

    data class Update(override val description: String, val identOpplysninger: PdlEndringOpplysning) : Result()
    data class NoUpdate(override val description: String) : Result()

    private fun UtenlandskId.erPersonValidertPaaLand(): Boolean = landspesifikkValidering.validerLandsspesifikkUID(land, id)

}

