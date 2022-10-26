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
class IdentOppdatering(
    private val euxService: EuxService,
    private val oppgaveHandler: OppgaveHandler,
    private val kodeverkClient: KodeverkClient,
    private val personService: PersonService,
    private val landspesifikkValidering: LandspesifikkValidering
) {

    private val logger = LoggerFactory.getLogger(IdentOppdatering::class.java)
    private val secureLogger = LoggerFactory.getLogger("secureLog")

    fun oppdaterUtenlandskIdent(sedHendelse: SedHendelse): Result {
        require(erRelevantForEESSIPensjon(sedHendelse)) { return NoUpdate("Ikke relevant for eessipensjon, buc: ${sedHendelse.bucType}, sed: ${sedHendelse.sedType}, sektor: ${sedHendelse.sektorKode}", "Ikke relevant for eessipensjon") }

        val sed = euxService.hentSed(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)
            .also { secureLogger.debug("SED:\n$it") }

        require(sedHendelse.avsenderLand.isNullOrEmpty().not()) {
            return NoUpdate("Avsenderland mangler")
        }

        val utenlandskPinItemFraSed =
            (sed.nav?.bruker?.person?.pin?.filter { it.land == sedHendelse.avsenderLand && it.land != "NO" }?: emptyList())
                .also {
                    check(it.isNotEmpty()) {
                        return NoUpdate("Bruker har ikke utenlandsk ident fra avsenderland (${sedHendelse.avsenderLand})", "Bruker har ikke utenlandsk ident fra avsenderland")
                    }
                }
                .also {
                    if(it.size >= 2) {
                        logger.info("Bruker har ${it.size} uider fra avsenderland (${sedHendelse.avsenderLand}) - Vi bruker den første.")
                    }
                }
                .first()
                .let {
                    UtenlandskId(landspesifikkValidering.normalisertPin(it.identifikator!!, it.land!!), it.land!!)
                }
                .also {
                    check(it.erPersonValidertPaaLand()) {
                        return NoUpdate(
                            "Utenlandsk id \"${it.id}\" er ikke på gyldig format for land ${it.land}",
                            "Utenlandsk id er ikke på gyldig format")
                    }
                }

        require(sedHendelse.avsenderNavn.isNullOrEmpty().not()) {
            return NoUpdate("AvsenderNavn er ikke satt, kan derfor ikke lage endringsmelding")
        }

        val personFraPDL =
            (norskPin(brukerFra(sed)) ?: return NoUpdate("Bruker har ikke norsk pin i SED"))
                .runCatching {
                    normaliserNorskPin(this.identifikator!!)
                }
                .recoverCatching {
                    if (it is IllegalArgumentException) return NoUpdate("Brukers norske id fra SED validerer ikke")
                    throw it
                }
                .mapCatching {
                    personService.hentPerson(NorskIdent(it)) ?: throw NullPointerException("hentPerson returnerte null")
                }
                .recoverCatching {
                    if (it is PersonoppslagException && it.code == "not_found") {
                        return NoUpdate("Finner ikke bruker i PDL med angitt fnr i SED")
                    }
                    throw it
                }
                .onSuccess {
                    secureLogger.debug("Person fra PDL:\n${it.toJson()}")
                }
                .getOrThrow()

        val norskFnr = personFraPDL.identer.first { it.gruppe == IdentGruppe.FOLKEREGISTERIDENT }.ident

        require(!utenlandskPinFinnesIPdl(utenlandskPinItemFraSed, personFraPDL.utenlandskIdentifikasjonsnummer)) {
            return NoUpdate("PDL uid er identisk med SED uid")
        }

        if (fraSammeLandMenUlikUid(utenlandskPinItemFraSed, personFraPDL.utenlandskIdentifikasjonsnummer)) {
            return opprettOppgave(personFraPDL, utenlandskPinItemFraSed, sedHendelse) // TODO: Burde vi ikke bruke norskFnr her?
        }

        logger.info("Oppdaterer PDL med Ny Utenlandsk Ident fra ${sedHendelse.avsenderNavn}")
        return Update("Innsending av endringsmelding", pdlEndringOpplysning(
            norskFnr,
                utenlandskPinItemFraSed,
                sedHendelse.avsenderNavn!!
            ),
        )
    }

    private fun fraSammeLandMenUlikUid(
        utenlandskIdFraSED: UtenlandskId,
        utenlandskeIderFraPDL: List<UtenlandskIdentifikasjonsnummer>): Boolean =

        utenlandskeIderFraPDL
            .filter { it.identifikasjonsnummer != utenlandskIdFraSED.id }
            .map { it.utstederland }
            .contains(kodeverkClient.finnLandkode(utenlandskIdFraSED.land))

    private fun opprettOppgave(
        personFraPDL: Person,
        utenlandskPinFraSed: UtenlandskId,
        sedHendelse: SedHendelse
    ): Result {
        return if (oppgaveHandler.opprettOppgaveForUid(
                sedHendelse,
                utenlandskPinFraSed,
                identifisertPerson(personFraPDL)
            )) {
            NoUpdate("Det finnes allerede en annen uid fra samme land (oppgave opprettes)")
        } else NoUpdate("Oppgave opprettet tidligere")
    }

    private fun utenlandskPinFinnesIPdl(utenlandskPin: UtenlandskId, utenlandskeUids: List<UtenlandskIdentifikasjonsnummer>) =
        utenlandskeUids.map { Pair(it.identifikasjonsnummer, kodeverkClient.finnLandkode(it.utstederland)) }
            .contains(Pair(utenlandskPin.id, utenlandskPin.land))

    private fun identifisertPerson(personFraPDL: Person) = IdentifisertPerson(
        fnr = Fodselsnummer.fra(personFraPDL.identer.first { it.gruppe == IdentGruppe.FOLKEREGISTERIDENT }.ident),
        uidFraPdl = personFraPDL.utenlandskIdentifikasjonsnummer,
        aktoerId = personFraPDL.identer.first { it.gruppe == IdentGruppe.AKTORID }.ident,
        landkode = personFraPDL.landkode(),
        geografiskTilknytning = personFraPDL.geografiskTilknytning?.gtKommune?: personFraPDL.geografiskTilknytning?.gtBydel,
        harAdressebeskyttelse = false,
        personListe = null,
        personRelasjon = null,
        erDoed = personFraPDL.erDoed(),
        kontaktAdresse = personFraPDL.kontaktadresse
    )

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
    private fun UtenlandskId.erPersonValidertPaaLand(): Boolean = landspesifikkValidering.validerLandsspesifikkUID(land, id)

    sealed class Result {
        abstract val description: String
        abstract val metricTagValueOverride: String?

        val metricTagValue: String
            get() = metricTagValueOverride ?: description
    }

    data class Update(override val description: String, val pdlEndringsOpplysninger: PdlEndringOpplysning, override val metricTagValueOverride: String? = null, ): Result()
    data class NoUpdate(override val description: String, override val metricTagValueOverride: String? = null): Result()

}

