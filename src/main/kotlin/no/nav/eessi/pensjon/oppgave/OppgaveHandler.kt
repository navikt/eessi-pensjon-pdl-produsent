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
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPersonPDL
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import javax.annotation.PostConstruct

private const val LAGRING_IDENT = "_IDENT"
private const val LAGRING_GJENLEV = "_GJENLEV"

@Service
class OppgaveHandler(
    private val oppgaveKafkaTemplate: KafkaTemplate<String, String>,
    private val lagringsService: LagringsService,
    val safClient: SafClient,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) : OppgaveOppslag {

    private val logger = LoggerFactory.getLogger(OppgaveHandler::class.java)
    private val X_REQUEST_ID = "x_request_id"

    private lateinit var publiserOppgavemelding: MetricsHelper.Metric
    private lateinit var oppgaveForUid: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        publiserOppgavemelding = metricsHelper.init("publiserOppgavemelding")
        oppgaveForUid = metricsHelper.init("OppgaveForUid")
    }

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
                val oppgaveEnhet = tildeltOppgaveEnhet(identifisertePerson.aktoerId, sedHendelse.rinaSakId, identifisertePerson)
                val melding = OppgaveMelding(
                    aktoerId = identifisertePerson.aktoerId,
                    filnavn = null,
                    sedType = null,
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

    private fun tildeltOppgaveEnhet(aktoerId: String, rinaSakId: String, identifisertePerson: IdentifisertPersonPDL): Enhet {
        val journalpost = hentJournalpostForRinasak(hentRinasakerForAktoerId(aktoerId), rinaSakId)
        val enhet = journalpost?.journalfoerendeEnhet
        val behandlingstema = journalpost?.behandlingstema

        logger.info("landkode: ${identifisertePerson.landkode} og behandlingstema: $behandlingstema")
        if (enhet == AUTOMATISK_JOURNALFORING.enhetsNr) {
            return if (identifisertePerson.landkode == "NO") {
                when (behandlingstema) {
                    GJENLEVENDEPENSJON.name, BARNEP.name -> NFP_UTLAND_AALESUND
                    ALDERSPENSJON.name -> NFP_UTLAND_AALESUND
                    UFOREPENSJON.name -> UFORE_UTLANDSTILSNITT
                    else -> Companion.getEnhet(enhet)!!
                }
            } else when (behandlingstema) {
                UFOREPENSJON.name -> UFORE_UTLANDSTILSNITT
                GJENLEVENDEPENSJON.name, BARNEP.name, ALDERSPENSJON.name -> PENSJON_UTLAND
                else -> Companion.getEnhet(enhet)!!
            }
        }
        return Companion.getEnhet(enhet!!)!!
    }

    override fun finnesOppgavenAllerede(rinaSakId: String) = !lagringsService.kanHendelsenOpprettes(rinaSakId)
    override fun finnesOppgavenAlleredeForUID(rinaSakId: String) = !lagringsService.kanHendelsenOpprettes(rinaSakId.plus(LAGRING_IDENT))
    override fun finnesOppgavenAlleredeGJENLEV(rinaSakId: String) = !lagringsService.kanHendelsenOpprettes(rinaSakId.plus(LAGRING_GJENLEV))


    private fun opprettOppgaveRuting(sedHendelse: SedHendelse, identifisertePerson : IdentifisertPersonPDL) : Enhet {
        return oppgaveruting.route(OppgaveRoutingRequest.fra(
            identifisertePerson,
            identifisertePerson.fnr!!.getBirthDate(),
            identifisertePerson.personRelasjon?.saktype,
            sedHendelse,
            HendelseType.MOTTATT,
            null,
            identifisertePerson.harAdressebeskyttelse!!
        ))
    }

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