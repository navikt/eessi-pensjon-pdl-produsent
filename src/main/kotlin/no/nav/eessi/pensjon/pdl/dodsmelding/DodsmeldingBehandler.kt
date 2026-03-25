package no.nav.eessi.pensjon.pdl.dodsmelding

import no.nav.eessi.pensjon.klienter.saf.BrukerIdType
import no.nav.eessi.pensjon.klienter.saf.SafClient
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.Ident
import no.nav.eessi.pensjon.utils.toJson
import no.nav.person.pdl.leesah.Personhendelse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import kotlin.text.get

@Component
class DodsmeldingBehandler(
	private val safClient: SafClient,
	private val personService: PersonService,
) {
	val gyldigeUtstederland = listOf("SWE", "FIN", "POL")

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

		if (!landFraIdentUtland.isNullOrEmpty()) {
			if (landFraIdentUtland.any { it in gyldigeUtstederland }) {
				logger.info("$landFraIdentUtland har utenlandskIdentifikasjonsnummer, henter dokumentmetadata fra saf")
				val responseFraSaf = safClient.hentDokumentMetadata(valgtPersonident, BrukerIdType.FNR)

				responseFraSaf.data.dokumentoversiktBruker.journalposter.forEach { journalpost ->
					logger.info("JournalpostId: ${journalpost.journalpostId}, datoOpprettet: ${journalpost.datoOpprettet}, tittel: ${journalpost.tittel}, journalfoerendeEnhet: ${journalpost.tilleggsopplysninger}")

					val bucid = journalpost.tilleggsopplysninger
					.firstNotNullOfOrNull { tilleggsopplysning -> tilleggsopplysning["eessi_pensjon_bucid"] }

					if (bucid != null) {
					    val dokumentFraSaf = safClient.hentDokumentInnhold(journalpost.journalpostId, bucid, "ARKIV")
						logger.info("ResponseFraSaf: {}", dokumentFraSaf.toJson())
					}
				}

				logger.info("Svar fra saf: $responseFraSaf")
			} else {
				logger.info("${landFraIdentUtland.toJson()} er ikke inkludert i listen: $gyldigeUtstederland, henter ikke dokumentmetadata fra saf")
			}
		} else {
			logger.info("Ingen utenlandskIdentifikasjonsnummer funnet, henter ikke dokumentmetadata fra saf")
		}
	}

	private fun hentAlleNorskeIdenter(personhendelse: Personhendelse?): String? {
		val valgtPersonident = personhendelse?.personidenter
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

