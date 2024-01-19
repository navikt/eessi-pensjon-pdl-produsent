package no.nav.eessi.pensjon.oppgave

import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.klienter.saf.Journalpost
import no.nav.eessi.pensjon.klienter.saf.SafClient
import no.nav.eessi.pensjon.lagring.LagringsService
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.oppgave.Behandlingstema.*
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.Enhet.*
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingRequest
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingService
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPersonPDL
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

private const val LAGRING_IDENT = "_IDENT"
private const val LAGRING_GJENLEV = "_GJENLEV"

@Service
class OppgaveHandler(
    private val oppgaveKafkaTemplate: KafkaTemplate<String, String>,
    private val lagringsService: LagringsService,
    private val oppgaveruting: OppgaveRoutingService,
    val safClient: SafClient,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) : OppgaveOppslag {

    private val logger = LoggerFactory.getLogger(OppgaveHandler::class.java)
    private val X_REQUEST_ID = "x_request_id"

    private var publiserOppgavemelding: MetricsHelper.Metric = metricsHelper.init("publiserOppgavemelding")
    private var oppgaveForUid: MetricsHelper.Metric = metricsHelper.init("OppgaveForUid")

    fun opprettOppgave(oppgaveData: OppgaveData): Boolean {
        return if(oppgaveData is OppgaveDataUID){
            opprettOppgave(oppgaveData.sedHendelse, oppgaveData.identifisertPerson, LAGRING_IDENT)
        } else {
            opprettOppgave(oppgaveData.sedHendelse, oppgaveData.identifisertPerson, LAGRING_GJENLEV)
        }
    }

    private fun hentJournalpostForRinasak(listeOverJournalposterForAktoerId: List<Journalpost>, rinaSakId:String) : Journalpost? {
        return listeOverJournalposterForAktoerId.filter {
                journalpost -> journalpost.tilleggsopplysninger.any {
            it.containsKey("eessi_pensjon_bucid")
            it.containsValue(rinaSakId)
        }
        }.distinctBy { it.behandlingstema }.firstOrNull()
    }

    private fun hentRinasakerForAktoerId(aktoerId: String): List<Journalpost> {
        val hentMetadataResponse = safClient.hentDokumentMetadata(aktoerId)
        return hentMetadataResponse.data.dokumentoversiktBruker.journalposter

    }

    private fun opprettOppgave(sedHendelse: SedHendelse, identifisertePerson: IdentifisertPersonPDL, lagringsPathPostfix: String): Boolean {
        return oppgaveForUid.measure {
            return@measure if (!finnesOppgavenAllerede(sedHendelse.rinaSakId.plus(lagringsPathPostfix))) {
                val oppgaveEnhet = tildeltOppgaveEnhet(identifisertePerson.aktoerId, sedHendelse, identifisertePerson)

                val melding = OppgaveMelding(
                    aktoerId = identifisertePerson.aktoerId,
                    filnavn = sedHendelse.sedType?.beskrivelse,
                    sedType = sedHendelse.sedType,
                    tildeltEnhetsnr = oppgaveEnhet,
                    rinaSakId = sedHendelse.rinaSakId,
                    hendelseType = HendelseType.MOTTATT,
                    oppgaveType = OppgaveType.PDL
                ).also { logger.info("Oppgaven ${lagringsPathPostfix.replace("_", "")} ble sendt til ${it.tildeltEnhetsnr}.") }

                opprettOppgaveMeldingPaaKafkaTopic(melding)
                lagringsService.lagreHendelseMedSakId(sedHendelse.rinaSakId.plus(lagringsPathPostfix))
                logger.info("Opprett oppgave og lagret til s3")
                true
            } else {
                logger.info("Hendelse finnes fra før i bucket. Oppgave blir derfor ikke opprettet")
                false
            }
        }
    }

    private fun tildeltOppgaveEnhet(aktoerId: String, sedHendelse: SedHendelse, identifisertePerson: IdentifisertPersonPDL): Enhet {

        try {
            val journalpost = hentJournalpostForRinasak(hentRinasakerForAktoerId(aktoerId), sedHendelse.rinaSakId)
//            val enhet = journalpost?.journalfoerendeEnhet
            val behandlingstema = journalpost?.behandlingstema

            logger.info("landkode: ${identifisertePerson.landkode} og behandlingstema: $behandlingstema")
//            if (enhet == AUTOMATISK_JOURNALFORING.enhetsNr) {
                return if (identifisertePerson.landkode == "NOR" ) {
                    when (Behandlingstema.hentKode(behandlingstema!!)) {
                        GJENLEVENDEPENSJON, BARNEP -> NFP_UTLAND_AALESUND
                        ALDERSPENSJON -> NFP_UTLAND_AALESUND
                        UFOREPENSJON -> UFORE_UTLANDSTILSNITT
                        else -> opprettOppgaveRuting(sedHendelse, identifisertePerson)
                    }
                } else when (Behandlingstema.hentKode(behandlingstema!!)) {
                    UFOREPENSJON -> UFORE_UTLANDSTILSNITT
                    GJENLEVENDEPENSJON, BARNEP, ALDERSPENSJON -> PENSJON_UTLAND
                    else -> opprettOppgaveRuting(sedHendelse, identifisertePerson)
                }
//            }
//            return Enhet.getEnhet(enhet!!)!!
        }
        catch (ex :Exception) {
            logger.warn("Henting fra joark feiler, forsøker manuell oppgave-ruting")
            return opprettOppgaveRuting(sedHendelse, identifisertePerson)
        }

    }
    private fun opprettOppgaveRuting(sedHendelse: SedHendelse, identifisertePerson : IdentifisertPersonPDL) : Enhet {
        logger.warn("Routing ihht tildeltOppgaveEnhet fungerer ikke; prøver oppgaverouting")
        return oppgaveruting.route(
            OppgaveRoutingRequest.fra(
            identifisertePerson,
            identifisertePerson.fnr!!.getBirthDate()!!,
            identifisertePerson.personRelasjon?.saktype,
            sedHendelse,
            HendelseType.MOTTATT,
            null,
            identifisertePerson.harAdressebeskyttelse!!
        ))
    }

    override fun finnesOppgavenAllerede(rinaSakId: String) = !lagringsService.kanHendelsenOpprettes(rinaSakId)
    override fun finnesOppgavenAlleredeForUID(rinaSakId: String) = !lagringsService.kanHendelsenOpprettes(rinaSakId.plus(LAGRING_IDENT))
    override fun finnesOppgavenAlleredeGJENLEV(rinaSakId: String) = !lagringsService.kanHendelsenOpprettes(rinaSakId.plus(LAGRING_GJENLEV))

  private fun opprettOppgaveMeldingPaaKafkaTopic(melding: OppgaveMelding) {
        val key = MDC.get(X_REQUEST_ID)
        val payload = melding.toJson()

        publiserOppgavemelding.measure {
            logger.info("Opprette oppgave melding på kafka: ${oppgaveKafkaTemplate.defaultTopic}  melding: $melding")
            oppgaveKafkaTemplate.sendDefault(key, payload).get()
        }
    }
}

interface OppgaveOppslag {
    fun finnesOppgavenAllerede(rinaSakId: String): Boolean
    fun finnesOppgavenAlleredeForUID(rinaSakId: String): Boolean
    fun finnesOppgavenAlleredeGJENLEV(rinaSakId: String): Boolean
}

interface OppgaveData {
    val sedHendelse: SedHendelse
    val identifisertPerson: IdentifisertPersonPDL
}

data class OppgaveDataUID(
    override val sedHendelse: SedHendelse,
    override val identifisertPerson: IdentifisertPersonPDL
) : OppgaveData

data class OppgaveDataGjenlevUID(
    override val sedHendelse: SedHendelse,
    override val identifisertPerson: IdentifisertPersonPDL,
) : OppgaveData