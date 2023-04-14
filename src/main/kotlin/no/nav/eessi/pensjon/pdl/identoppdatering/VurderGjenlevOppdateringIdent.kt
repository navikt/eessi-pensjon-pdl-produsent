package no.nav.eessi.pensjon.pdl.identoppdatering

import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.UtenlandskId
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.models.EndringsmeldingUID
import no.nav.eessi.pensjon.models.PdlEndringOpplysning
import no.nav.eessi.pensjon.models.Personopplysninger
import no.nav.eessi.pensjon.oppgave.OppgaveData
import no.nav.eessi.pensjon.oppgave.OppgaveOppslag
import no.nav.eessi.pensjon.pdl.validering.LandspesifikkValidering
import no.nav.eessi.pensjon.pdl.validering.erRelevantForEESSIPensjon
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPersonPDL
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.PersonoppslagException
import no.nav.eessi.pensjon.personoppslag.pdl.model.*
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

    fun vurderUtenlandskGjenlevIdent(sedHendelse: SedHendelse): VurderIdentoppdatering.Result {
        require(erRelevantForEESSIPensjon(sedHendelse)) { return VurderIdentoppdatering.IngenOppdatering("Ikke relevant for eessipensjon, buc: ${sedHendelse.bucType}, sed: ${sedHendelse.sedType}, sektor: ${sedHendelse.sektorKode}", "Ikke relevant for eessipensjon") }

        val sed = euxService.hentSed(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)
            .also { secureLogger.debug("SED:\n$it") }

        require(sedHendelse.avsenderLand.isNullOrEmpty().not()) {
            return VurderIdentoppdatering.IngenOppdatering("Avsenderland mangler")
        }

        val uidGjenlevendeFraSed  =
            (sed.pensjon?.gjenlevende?.person?.pin?.filter { it.land == sedHendelse.avsenderLand && it.land != "NO" }?: emptyList())
                .also {
                    if (it.isEmpty()) {
                        return VurderIdentoppdatering.IngenOppdatering("Gjenlevende bruker har ikke utenlandsk ident fra avsenderland (${sedHendelse.avsenderLand})", "Gjenlevende bruker har ikke utenlandsk ident fra avsenderland")
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
                        0 -> return VurderIdentoppdatering.IngenOppdatering(
                                "Utenlandsk(e) id(-er) er ikke på gyldig format for land ${sedHendelse.avsenderLand}",
                                "Utenlandsk id er ikke på gyldig format")
                        1 -> logger.info("Gjenlevende bruker har én utenlandsk id fra avsenderland (${sedHendelse.avsenderLand}) som validerer.")
                        else -> logger.warn("Bruker har ${it.size} uider fra avsenderland (${sedHendelse.avsenderLand}) også etter deduplisering og validering. Bruker den første.")
                    }
                }.first()

        require(sedHendelse.avsenderNavn.isNullOrEmpty().not()) {
            return VurderIdentoppdatering.IngenOppdatering("AvsenderNavn er ikke satt, kan derfor ikke lage endringsmelding")
        }

        val personFraPDL =
            (norskPin(brukerFra(sed)) ?: return VurderIdentoppdatering.IngenOppdatering("Gjenlevende bruker har ikke norsk pin i SED"))
                .runCatching {
                    normaliserNorskPin(this.identifikator!!)
                }
                .recoverCatching {
                    if (it is IllegalArgumentException) return VurderIdentoppdatering.IngenOppdatering("Gjenlevende brukers norske id fra SED validerer ikke")
                    throw it
                }
                .mapCatching {
                    personService.hentPerson(NorskIdent(it)) ?: throw NullPointerException("hentPerson returnerte null")
                }
                .recoverCatching {
                    if (it is PersonoppslagException && it.code == "not_found") {
                        return VurderIdentoppdatering.IngenOppdatering("Finner ikke gjenlevende bruker i PDL med angitt fnr i SED")
                    }
                    throw it
                }
                .onSuccess {
                    secureLogger.debug("Person fra PDL:\n${it.toJson()}")
                }
                .getOrThrow()

        val norskFnr = personFraPDL.identer.first { it.gruppe == IdentGruppe.FOLKEREGISTERIDENT }.ident

        require(!utenlandskPinFinnesIPdl(uidGjenlevendeFraSed, personFraPDL.utenlandskIdentifikasjonsnummer)) {
            return VurderIdentoppdatering.IngenOppdatering("PDL uid er identisk med SED uid")
        }

        if (fraSammeLandMenUlikUid(uidGjenlevendeFraSed, personFraPDL.utenlandskIdentifikasjonsnummer)) {
            return if (!oppgaveOppslag.finnesOppgavenAllerede(sedHendelse.rinaSakId)) {
                VurderIdentoppdatering.Oppgave(
                    "Det finnes allerede en annen uid fra samme land (oppgave opprettes)", OppgaveData(
                        sedHendelse,
                        identifisertPerson(personFraPDL)
                    )
                )
            } else {
                VurderIdentoppdatering.IngenOppdatering("Oppgave opprettet tidligere")
            }
        }

        logger.info("Oppdaterer PDL med Ny Utenlandsk Ident fra ${sedHendelse.avsenderNavn}")
        return VurderIdentoppdatering.Oppdatering(
            "Innsending av endringsmelding",
            pdlEndringOpplysning(
                norskFnr,
                uidGjenlevendeFraSed,
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

}

