package no.nav.eessi.pensjon.pdl.identoppdatering

import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.UtenlandskId
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.kodeverk.KodeverkClient.Companion.toJson
import no.nav.eessi.pensjon.models.EndringsmeldingUID
import no.nav.eessi.pensjon.models.PdlEndringOpplysning
import no.nav.eessi.pensjon.models.Personopplysninger
import no.nav.eessi.pensjon.oppgave.OppgaveData
import no.nav.eessi.pensjon.oppgave.OppgaveDataUID
import no.nav.eessi.pensjon.oppgave.OppgaveOppslag
import no.nav.eessi.pensjon.pdl.validering.LandspesifikkValidering
import no.nav.eessi.pensjon.pdl.validering.erRelevantForEESSIPensjon
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPersonPDL
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.PersonoppslagException
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endringstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.Opplysningstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Person
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskIdentifikasjonsnummer
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

@Service
class VurderIdentoppdatering(
    private val euxService: EuxService,
    @Qualifier("oppgaveHandler") private val oppgaveOppslag: OppgaveOppslag,
    private val kodeverkClient: KodeverkClient,
    private val personService: PersonService,
    private val landspesifikkValidering: LandspesifikkValidering
) {

    private val logger = LoggerFactory.getLogger(VurderIdentoppdatering::class.java)
    private val secureLogger = LoggerFactory.getLogger("secureLog")

    fun vurderUtenlandskIdent(sedHendelse: SedHendelse): Result {
        require(erRelevantForEESSIPensjon(sedHendelse)) { return IngenOppdatering("Ikke relevant for eessipensjon, buc: ${sedHendelse.bucType}, sed: ${sedHendelse.sedType}, sektor: ${sedHendelse.sektorKode}", "Ikke relevant for eessipensjon") }

        val sed = euxService.hentSed(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)
            .also { secureLogger.debug("SED:\n${it}") }

        require(sedHendelse.avsenderLand.isNullOrEmpty().not()) {
            return IngenOppdatering("Avsenderland mangler")
        }

        val utenlandskIdFraSED =
            (sed.nav?.bruker?.person?.pin?.filter { it.land == sedHendelse.avsenderLand && it.land != "NO" }?: emptyList())
                .also {
                    if (it.isEmpty()) {
                        return IngenOppdatering("Bruker har ikke utenlandsk ident fra avsenderland (${sedHendelse.avsenderLand})", "Bruker har ikke utenlandsk ident fra avsenderland")
                    }
                }
                .also {
                    if(it.size > 1) {
                        logger.info("Bruker har ${it.size} uider fra avsenderland (${sedHendelse.avsenderLand}).")
                    }
                }
                .map {
                    UtenlandskId(landspesifikkValidering.normalisertPin(it.identifikator!!, it.land!!), it.land!!)
                }
                .toSet() // deduplication
                .filter { it.erPersonValidertPaaLand() } // validering
                .also {
                    when (it.size) {
                        0 -> return IngenOppdatering(
                                "Utenlandsk(e) id(-er) er ikke på gyldig format for land ${sedHendelse.avsenderLand}",
                                "Utenlandsk id er ikke på gyldig format")
                        1 -> logger.info("Bruker har én utenlandsk id fra avsenderland (${sedHendelse.avsenderLand}) som validerer.")
                        else -> logger.warn("Bruker har ${it.size} uider fra avsenderland (${sedHendelse.avsenderLand}) også etter deduplisering og validering. Bruker den første.")
                    }
                }.first()

        require(sedHendelse.avsenderNavn.isNullOrEmpty().not()) {
            return IngenOppdatering("AvsenderNavn er ikke satt, kan derfor ikke lage endringsmelding")
        }

        val personFraPDL =
            (norskPin(brukerFra(sed)) ?: return IngenOppdatering("Bruker har ikke norsk pin i SED"))
                .runCatching {
                    normaliserNorskPin(this.identifikator!!)
                }
                .recoverCatching {
                    if (it is IllegalArgumentException) return IngenOppdatering("Brukers norske id fra SED validerer ikke")
                    throw it
                }
                .mapCatching {
                    personService.hentPerson(NorskIdent(it)) ?: throw NullPointerException("hentPerson returnerte null")
                }
                .recoverCatching {
                    if (it is PersonoppslagException && it.code == "not_found") {
                        return IngenOppdatering("Finner ikke bruker i PDL med angitt fnr i SED")
                    }
                    throw it
                }
                .onSuccess {
                    secureLogger.debug("Person fra PDL:\n${it}")
                }
                .getOrThrow()

        val norskFnr = personFraPDL.identer.first { it.gruppe == IdentGruppe.FOLKEREGISTERIDENT }.ident

        require(!utenlandskPinFinnesIPdl(utenlandskIdFraSED, personFraPDL.utenlandskIdentifikasjonsnummer)) {
            return IngenOppdatering("PDL uid er identisk med SED uid")
        }

        if (fraSammeLandMenUlikUid(utenlandskIdFraSED, personFraPDL.utenlandskIdentifikasjonsnummer)) {
            return if (!oppgaveOppslag.finnesOppgavenAllerede(sedHendelse.rinaSakId)) {
                Oppgave(
                    "Det finnes allerede en annen uid fra samme land (oppgave opprettes)", OppgaveDataUID(
                        sedHendelse,
                        identifisertPerson(personFraPDL)
                    )
                )
            } else {
                IngenOppdatering("Oppgave opprettet tidligere")
            }
        }

        logger.info("Oppdaterer PDL med Ny Utenlandsk Ident fra ${sedHendelse.avsenderNavn}")
        return Oppdatering("Innsending av endringsmelding", pdlEndringOpplysning(
            norskFnr,
                utenlandskIdFraSED,
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

    private fun utenlandskPinFinnesIPdl(utenlandskPin: UtenlandskId, utenlandskeUids: List<UtenlandskIdentifikasjonsnummer>) =
        utenlandskeUids.map { Pair(it.identifikasjonsnummer, kodeverkClient.finnLandkode(it.utstederland)) }
            .contains(Pair(utenlandskPin.id, utenlandskPin.land))

    private fun identifisertPerson(personFraPDL: Person) = IdentifisertPersonPDL(
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
        if (utenlandskPin.land == "DK" || utenlandskPin.land == "IS") {
            return landspesifikkValidering.formaterDanskEllerIslandskUID(utenlandskPin.id)
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

    data class Oppdatering(override val description: String, val pdlEndringsOpplysninger: PdlEndringOpplysning, override val metricTagValueOverride: String? = null, ): Result()
    data class IngenOppdatering(override val description: String, override val metricTagValueOverride: String? = null): Result()
    data class Oppgave(override val description: String, val oppgaveData: OppgaveData, override val metricTagValueOverride: String? = null): Result()


}

