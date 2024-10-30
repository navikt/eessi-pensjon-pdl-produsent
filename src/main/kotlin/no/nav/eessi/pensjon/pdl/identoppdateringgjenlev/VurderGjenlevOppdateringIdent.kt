package no.nav.eessi.pensjon.pdl.identoppdateringgjenlev

import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.UtenlandskId
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.sed.*
import no.nav.eessi.pensjon.klienter.saf.SafClient
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.oppgave.OppgaveDataGjenlevUID
import no.nav.eessi.pensjon.oppgave.OppgaveOppslag
import no.nav.eessi.pensjon.pdl.EndringsmeldingUID
import no.nav.eessi.pensjon.pdl.OppgaveModel
import no.nav.eessi.pensjon.pdl.PdlEndringOpplysning
import no.nav.eessi.pensjon.pdl.Personopplysninger
import no.nav.eessi.pensjon.pdl.validering.LandspesifikkValidering
import no.nav.eessi.pensjon.pdl.validering.erRelevantForEESSIPensjon
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPersonPDL
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.PersonoppslagException
import no.nav.eessi.pensjon.personoppslag.pdl.model.*
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe.*
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class VurderGjenlevOppdateringIdent(
    private val euxService: EuxService,
    @Qualifier("oppgaveHandler") private val oppgaveOppslag: OppgaveOppslag,
    private val kodeverkClient: KodeverkClient,
    private val personService: PersonService,
    private val landspesifikkValidering: LandspesifikkValidering,
    private val safClient: SafClient
): OppgaveModel() {

    private val logger = LoggerFactory.getLogger(VurderGjenlevOppdateringIdent::class.java)
    private val secureLogger = LoggerFactory.getLogger("secureLog")

    fun vurderUtenlandskGjenlevIdent(sedHendelse: SedHendelse): Result {
        require(erRelevantForEESSIPensjon(sedHendelse)) { return IngenOppdatering("Ikke relevant for eessipensjon, buc: ${sedHendelse.bucType}, sed: ${sedHendelse.sedType}, sektor: ${sedHendelse.sektorKode}", "Ikke relevant for eessipensjon") }

        val sed = euxService.hentSed(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)
            .also { secureLogger.debug("SED:\n$it") }

        require(sedHendelse.avsenderLand.isNullOrEmpty().not()) {
            return IngenOppdatering("Avsenderland mangler")
        }

        val gjenlevendeFraSed = getGjenlev(sed).also { secureLogger.info("gjenlevende fra sed: $it") }
        val gjenlevendeNorskPin = gjenlevendeFraSed?.person?.pin?.firstOrNull { it.land == "NO" }?.identifikator.also { secureLogger.info("Gjenlevende Norsk pin: $it") }

        require((gjenlevendeFraSed == null || gjenlevendeNorskPin == null).not()) {
            if(gjenlevendeFraSed == null){
                return IngenOppdatering("Seden har ingen gjenlevende")
            }
            return IngenOppdatering("Seden har ingen norsk pin på gjenlevende")
        }

        if (gjenlevFdatoUlikLikGjenlevFnr(gjenlevendeNorskPin, gjenlevendeFraSed))
            return IngenOppdatering("Gjenlevende fdato stemmer ikke overens med fnr", "Gjenlevende fdato stemmer ikke overens med fnr")

        val gjenlevendeUid = gjenlevendeFraSed?.person?.pin?.filter { it.land == sedHendelse.avsenderLand && it.land != "NO" }
        secureLogger.debug("Gjenlevende person pin: ${gjenlevendeFraSed?.person?.pin} gjenlevende uid: $gjenlevendeUid")

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
                    personService.hentPerson(Ident.bestemIdent(it)) ?: throw NullPointerException("hentPerson returnerte null")
                }
                .recoverCatching {
                    if (it is PersonoppslagException && it.code == "not_found") {
                        return IngenOppdatering("Finner ikke gjenlevende bruker i PDL med angitt fnr i SED")
                    }
                    throw it
                }
                .onSuccess {
                    secureLogger.debug("Person fra PDL:\n${it}")
                }
                .getOrThrow()

        require(!utenlandskPinFinnesIPdl(uidGjenlevendeFraSed, personGjenlevFraPDL.utenlandskIdentifikasjonsnummer)) {
            return IngenOppdatering("PDL uid er identisk med SED uid")
        }

        if (fraSammeLandMenUlikUid(uidGjenlevendeFraSed, personGjenlevFraPDL.utenlandskIdentifikasjonsnummer)) {
            if (!oppgaveOppslag.finnesOppgavenAllerede(sedHendelse.rinaSakId)) {
                return OppgaveGjenlev(
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
                normaliserNorskPin(gjenlevNorskIdent),
                uidGjenlevendeFraSed,
                sedHendelse.avsenderNavn!!
            ),
        )
    }

    private fun gjenlevFdatoUlikLikGjenlevFnr(
        gjenlevendeNorskPin: String?,
        gjenlevendeFraSed: Bruker?
    ) : Boolean {
        return try {
            Fodselsnummer.fra(gjenlevendeNorskPin)?.getBirthDate() !=
                    LocalDate.parse(
                        gjenlevendeFraSed?.person?.foedselsdato,
                        DateTimeFormatter.ofPattern("yyyy-MM-dd")
                    )
        } catch (ex: Exception) {
            false
        }
    }

    private fun getGjenlev(sed: SED): Bruker? {
        return when(sed) {
            is P2100 -> sed.pensjon?.gjenlevende
            is P4000 -> sed.p4000Pensjon?.gjenlevende
            is P5000 -> sed.pensjon?.gjenlevende
            is P6000 -> sed.pensjon?.gjenlevende
            is P7000 -> sed.pensjon?.gjenlevende
            is P8000 -> sed.p8000Pensjon?.gjenlevende
            is P9000 -> sed.pensjon?.gjenlevende
            is P10000 -> sed.pensjon?.gjenlevende
            is P15000 -> sed.pensjon?.gjenlevende
            else -> {
                logger.warn("Sed: ${sed.type} er ikke en del av vurdering for gjenlevende ident")
                null
            }
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

    private fun identifisertPerson(personFraPDL: PdlPerson) = IdentifisertPersonPDL(
        fnr = Fodselsnummer.fra(personFraPDL.identer.first { it.gruppe == FOLKEREGISTERIDENT || it.gruppe == NPID }.ident),
        uidFraPdl = personFraPDL.utenlandskIdentifikasjonsnummer,
        aktoerId = personFraPDL.identer.first { it.gruppe == AKTORID }.ident,
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
                        kilde = if (kilde.contains("Employee Insurance UWV Amsterdam office")) formaterNederlandskKilde(kilde) else kilde
                    ),
                    opplysningstype = Opplysningstype.UTENLANDSKIDENTIFIKASJONSNUMMER
                )
            )
        )

    private fun formaterNederlandskKilde(kilde: String): String =
        kilde.replace("->", "")
            .replace("<-", "")
            .replace("<", "")
            .replace(">", "")

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


}

