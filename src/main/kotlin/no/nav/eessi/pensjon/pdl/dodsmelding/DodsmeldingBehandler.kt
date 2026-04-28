package no.nav.eessi.pensjon.pdl.dodsmelding

import no.nav.eessi.pensjon.klienter.saf.Journalpost
import no.nav.eessi.pensjon.klienter.saf.SafClient
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.Ident
import no.nav.eessi.pensjon.OpprettH070.OpprettH070
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.buc.SakType.ALDER
import no.nav.eessi.pensjon.eux.model.buc.SakType.BARNEP
import no.nav.eessi.pensjon.eux.model.buc.SakType.GJENLEV
import no.nav.eessi.pensjon.eux.model.buc.SakType.OMSORG
import no.nav.eessi.pensjon.eux.model.buc.SakType.UFOREP
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.utils.toJson
import no.nav.eessi.pensjon.utils.toJsonSkipEmpty
import no.nav.person.pdl.leesah.Personhendelse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class DodsmeldingBehandler(
	private val fagmodulKlient: FagmodulKlient,
	private val safClient: SafClient,
	private val personService: PersonService,
	private val opprettH070: OpprettH070,
	private val euxService: EuxService,
	@Value("\${ENV}") private val env: String
	) {
	val gyldigeUtstederland = listOf("SW", "SWE", "FI", "FIN",  "PO", "POL")

	private val logger: Logger = LoggerFactory.getLogger(DodsmeldingBehandler::class.java)

	fun behandle(personhendelse: Personhendelse) {
		val valgtPersonident = hentAlleNorskeIdenter(personhendelse)

		if (valgtPersonident == null) {
			logger.warn("Fant ingen gyldig ident i personidenter: ${personhendelse.personidenter}")
			return
		}

		logger.info("Henter informasjon for ident: ${valgtPersonident.take(4)}")
		val identFraPdl = Ident.bestemIdent(valgtPersonident)

		val person = personService.hentPerson(identFraPdl).also { logger.debug("Henter person: {}", it) }

		val landFraIdentUtland = person?.utenlandskIdentifikasjonsnummer
			?.map { it.utstederland }
			?.toSet().also { logger.debug("Henter land: {}", it) }

		when {
			landFraIdentUtland.isNullOrEmpty() -> {
				logger.info("Ingen utenlandskIdentifikasjonsnummer funnet, henter ikke dokumentmetadata fra saf")
			}
			landFraIdentUtland.none { it in gyldigeUtstederland } -> {
				logger.info("${landFraIdentUtland.toJson()} er ikke inkludert i listen: $gyldigeUtstederland, henter ikke dokumentmetadata fra saf")
			}
			else -> {
				logger.info("$landFraIdentUtland har utenlandskIdentifikasjonsnummer, henter dokumentmetadata fra saf")

//				val responseFraSaf = safClient.hentDokumentMetadata(valgtPersonident, BrukerIdType.FNR)
//
//				responseFraSaf.data.dokumentoversiktBruker.journalposter.forEach { journalpost ->
//					logger.info("JournalpostId: ${journalpost.journalpostId}, datoOpprettet: ${journalpost.datoOpprettet}, tittel: ${journalpost.tittel}, journalfoerendeEnhet: ${journalpost.tilleggsopplysninger}")
//
//					journalpost.dokumenter?.firstNotNullOfOrNull { it.dokumentInfoId }?.let { dokumentInfoId ->
//						val dokumentFraSaf = safClient.hentDokumentInnhold(journalpost.journalpostId, dokumentInfoId, "ARKIV")
//						logger.info("ResponseFraSaf: {}", dokumentFraSaf?.toJson())
//					}
//				}
//				logger.info("Svar fra saf: $responseFraSaf")

				//TODO: Sjekk hvilken ytelse bruker har før vi går videre med å preutfylle en H070

				val fnr = person?.identer?.firstOrNull { it.gruppe == IdentGruppe.FOLKEREGISTERIDENT }?.ident
				val institusjonViSkalSendeTil = institusjon(fnr, landFraIdentUtland)

				//Preutfyller en H070
				logger.info("Preutfyller H070 for bruker fra $landFraIdentUtland.")
				val h070 =  opprettH070.oppretterH070(personhendelse, person!!)

				//TODO: Sjekk hvilken institusjon som skal legges til ut i fra hvilket land det er som skal motta H070 fra oss.

				// Oppretter en H_BUC_07 med et utkast på H070
				try {
					if (env == "q2") {
						val response = euxService.opprettH070("NO:NAVAT05", h070)
						Thread.sleep(5000) // Legger inn en liten delay for å unngå at sendSed blir kalt før opprettH070 er ferdig.
						euxService.sendSed(response.caseId, response.documentId)
					} else {
						val response = euxService.opprettH070(institusjonViSkalSendeTil, h070)
						//Sender H070 til utlandet
						euxService.sendSed(response.caseId, response.documentId)
					}
				} catch (e: Exception) {
					logger.error("Feil ved opprettelse av H070", e)
					return
				}

				logger.debug("Oppretter H070: ${h070.toJsonSkipEmpty()}")
			}
		}
	}

	fun institusjon(fnr: String?, landFraIdentUtland: Set<String>): String {
		val ytelsesInfo = fagmodulKlient.hentPensjonSaklist(fnr!!).also { logger.debug("Henter pensjonsakliste: {}", it.toJson()) }
		val penytelse = ytelsesInfo.firstOrNull { it.sakType in listOf(UFOREP, GJENLEV, BARNEP, ALDER, OMSORG) }
		val land = if(landFraIdentUtland.contains("FIN" )) "FIN" else if (landFraIdentUtland.contains("SWE")) "SWE" else if (landFraIdentUtland.contains("POL")) "POL" else null
		val institusjonViSkalSendeTil = mottakendeInstitusjon(penytelse, land)
		return institusjonViSkalSendeTil
	}

	private fun mottakendeInstitusjon(penytelse: SakInformasjon?, land: String?) : String {
		//TODO: Avklaring om vi trenger å sende H070 til en annen institusjon i landet dersom ytelsen er forskjellig
		return when (land) {
			// Dersom bruker har en en uføre ytelse, sendes H070 til institusjon nummer 2 for Sverige
            "SE", "SWE" -> if (penytelse?.sakType == UFOREP) "SE:2001" else "SE:3002"
            "FI", "FIN" -> "FI:0200000010"
            "PL", "POL" -> "PL:PL390050ER"
            else -> throw IllegalArgumentException("Ugyldig land. $land er ikke en av de gyldige landene for opprettelse av H070")
        }
	}

	private fun hentBucId(journalpost: Journalpost): String? {
		val bucid = journalpost.tilleggsopplysninger
			.firstNotNullOfOrNull { tilleggsopplysning ->
				val nokkel = tilleggsopplysning["nokkel"]
				if (nokkel == "eessi_pensjon_bucid") {
					tilleggsopplysning["verdi"]
				} else {
					null
				}
			}
		return bucid
	}

	private fun hentAlleNorskeIdenter(personhendelse: Personhendelse?): String? {
		val valgtPersonident = personhendelse?.personidenter
			?.filter { it.length > 10 }
			?.firstOrNull { ident ->
				try {
					Ident.bestemIdent(ident)
					true
				} catch (e: Exception) {
					logger.debug("Ignorerer ident som ikke kan bestemmes: $ident", e)
					false
				}
			}
		return valgtPersonident
	}
}

