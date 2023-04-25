package no.nav.eessi.pensjon.pdl.identOppdateringGjenlev

import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.UtenlandskId
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.sed.*
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.models.EndringsmeldingUID
import no.nav.eessi.pensjon.models.PdlEndringOpplysning
import no.nav.eessi.pensjon.models.Personopplysninger
import no.nav.eessi.pensjon.oppgave.OppgaveData
import no.nav.eessi.pensjon.oppgave.OppgaveDataGjenlevUID
import no.nav.eessi.pensjon.oppgave.OppgaveOppslag
import no.nav.eessi.pensjon.pdl.validering.LandspesifikkValidering
import no.nav.eessi.pensjon.pdl.validering.erRelevantForEESSIPensjon
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPersonPDL
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.PersonoppslagException
import no.nav.eessi.pensjon.personoppslag.pdl.model.*
import no.nav.eessi.pensjon.personoppslag.pdl.model.Person
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

@Service
class VurderGjenlevOppdateringIdent(
    private val euxService: EuxService,
    @Qualifier("oppgaveHandler") private val oppgaveOppslag: OppgaveOppslag,
    private val kodeverkClient: KodeverkClient,
    private val personService: PersonService,
    private val landspesifikkValidering: LandspesifikkValidering
) {

    private val logger = LoggerFactory.getLogger(VurderGjenlevOppdateringIdent::class.java)
    private val secureLogger = LoggerFactory.getLogger("secureLog")

    fun vurderUtenlandskGjenlevIdent(sedHendelse: SedHendelse): Result {
        require(erRelevantForEESSIPensjon(sedHendelse)) { return IngenOppdatering("Ikke relevant for eessipensjon, buc: ${sedHendelse.bucType}, sed: ${sedHendelse.sedType}, sektor: ${sedHendelse.sektorKode}", "Ikke relevant for eessipensjon") }

        val sed = euxService.hentSed(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)
            .also { secureLogger.debug("SED:\n$it") }

        require(sedHendelse.avsenderLand.isNullOrEmpty().not()) {
            return IngenOppdatering("Avsenderland mangler")
        }

        val gjenlevendeFraSed = getGjenlev(sed)
        val gjenlevendeUid = gjenlevendeFraSed?.person?.pin?.filter { it.land == sedHendelse.avsenderLand && it.land != "NO" }

        val uidGjenlevendeFraSed  =
            (gjenlevendeUid ?: emptyList())
                .also {
                    if (it.isEmpty()) {
                        return IngenOppdatering("Gjenlevende bruker har ikke utenlandsk ident fra avsenderland (${sedHendelse.avsenderLand})", "Gjenlevende bruker har ikke utenlandsk ident fra avsenderland")
                    }
                }
                .also {
                    if(it.size > 1) {
                        logger.info("Gjenlevende bruker har ${it.size} uider fra avsenderland (${sedHendelse.avsenderLand}).")
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
                        1 -> logger.info("Gjenlevende bruker har én utenlandsk id fra avsenderland (${sedHendelse.avsenderLand}) som validerer.")
                        else -> logger.warn("Bruker har ${it.size} uider fra avsenderland (${sedHendelse.avsenderLand}) også etter deduplisering og validering. Bruker den første.")
                    }
                }.first()

        require(sedHendelse.avsenderNavn.isNullOrEmpty().not()) {
            return IngenOppdatering("AvsenderNavn er ikke satt, kan derfor ikke lage endringsmelding")
        }

        val gjenlevNorskIdent = gjenlevendeFraSed?.person?.pin?.first { it.land == "NO" }?.identifikator
        val personGjenlevFraPDL =
            (gjenlevNorskIdent ?: return IngenOppdatering("Gjenlevende bruker har ikke norsk pin i SED"))
                .runCatching {
                    normaliserNorskPin(this)
                }
                .recoverCatching {
                    if (it is IllegalArgumentException) return IngenOppdatering("Gjenlevende brukers norske id fra SED validerer ikke")
                    throw it
                }
                .mapCatching {
                    personService.hentPerson(NorskIdent(it)) ?: throw NullPointerException("hentPerson returnerte null")
                }
                .recoverCatching {
                    if (it is PersonoppslagException && it.code == "not_found") {
                        return IngenOppdatering("Finner ikke gjenlevende bruker i PDL med angitt fnr i SED")
                    }
                    throw it
                }
                .onSuccess {
                    secureLogger.debug("Person fra PDL:\n${it.toJson()}")
                }
                .getOrThrow()

        require(!utenlandskPinFinnesIPdl(uidGjenlevendeFraSed, personGjenlevFraPDL.utenlandskIdentifikasjonsnummer)) {
            return IngenOppdatering("PDL uid er identisk med SED uid")
        }

        if (fraSammeLandMenUlikUid(uidGjenlevendeFraSed, personGjenlevFraPDL.utenlandskIdentifikasjonsnummer)) {
            return if (!oppgaveOppslag.finnesOppgavenAllerede(sedHendelse.rinaSakId)) {
                Oppgave(
                    "Det finnes allerede en annen uid fra samme land (oppgave opprettes)", OppgaveDataGjenlevUID(
                        sedHendelse,
                        identifisertPerson(personGjenlevFraPDL)
                    )
                )
            } else {
                IngenOppdatering("Oppgave opprettet tidligere")
            }
        }

        logger.info("Oppdaterer PDL med Ny Utenlandsk Ident fra ${sedHendelse.avsenderNavn}")
        return Oppdatering(
            "Innsending av endringsmelding",
            pdlEndringOpplysning(
                gjenlevNorskIdent,
                uidGjenlevendeFraSed,
                sedHendelse.avsenderNavn!!
            ),
        )
    }

    private fun getGjenlev(sed: SED): Bruker? {
        return when(sed) {
            is P4000 -> sed.p4000Pensjon?.gjenlevende
            is P5000 -> sed.p5000Pensjon?.gjenlevende
            is P6000 -> sed.p6000Pensjon?.gjenlevende
            is P7000 -> sed.p7000Pensjon?.gjenlevende
            is P8000 -> sed.p8000Pensjon?.gjenlevende
            is P9000 -> sed.pensjon?.gjenlevende
            is P10000 -> sed.pensjon?.gjenlevende
            is P15000 -> sed.p15000Pensjon?.gjenlevende
            else -> null
        }
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

