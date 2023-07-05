package no.nav.eessi.pensjon.pdl.identOppdateringGjenlev

import io.micrometer.core.instrument.Metrics
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.oppgave.OppgaveHandler
import no.nav.eessi.pensjon.pdl.OppgaveModel.*
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class SedHendelseGjenlevIdentBehandler(
    private val vurderGjenlevOppdateringIdent: VurderGjenlevOppdateringIdent,
    private val personMottakKlient: PersonMottakKlient,
    private val oppgaveHandler: OppgaveHandler,
    @Value("\${SPRING_PROFILES_ACTIVE:}") private val profile: String
) {
    private val logger = LoggerFactory.getLogger(SedHendelseGjenlevIdentBehandler::class.java)
    private val secureLogger = LoggerFactory.getLogger("secureLog")

    fun behandlenGjenlevHendelse(hendelse: String) {
        logger.debug(hendelse)
        logger.debug("Profile: $profile")
        val sedHendelse = sedHendelseMapping(hendelse).also { secureLogger.debug("Sedhendelse:\n${it.toJson()}") }

        if (testHendelseIProd(sedHendelse)) {
            logger.error("Avsender id er ${sedHendelse.avsenderId}. Dette er testdata i produksjon!!!\n$sedHendelse")
            return
        }

        logger.info("*** Starter pdl endringsmelding (GJENLEV) prosess for BucType: ${sedHendelse.bucType}, SED: ${sedHendelse.sedType}, RinaSakID: ${sedHendelse.rinaSakId} ***")

        val result = vurderGjenlevOppdateringIdent.vurderUtenlandskGjenlevIdent(sedHendelse)

        log(result)
        when (result) {
            is Oppdatering -> {
//                personMottakKlient.opprettPersonopplysning(result.pdlEndringsOpplysninger)
                logger.debug("Her kommer det en opprettelse av personopplysning")
            }
            is OppgaveGjenlev -> {
//                oppgaveHandler.opprettOppgave(result.oppgaveData)
                logger.debug("Her kommer det en opprettelse av oppgave for Gjenlev")
            }
            is IngenOppdatering -> {
                logger.debug("Ingen oppgave")
            }
        }
        count(result.metricTagValue)
    }

    private fun log(result: Result) {
        when (result) {
            is Oppdatering -> {
                secureLogger.debug("Oppdatering:\n${result.toJson()}")
                logger.info("Oppdatering(description=${result.description})")
            }

            is IngenOppdatering -> {
                logger.info(result.toString())
            }

            else -> {
                logger.info("Oppgave(description=${result.description}")
            }
        }
    }

    private fun testHendelseIProd(sedHendelse: SedHendelse) =
            profile == "prod" && sedHendelse.avsenderId in listOf("NO:NAVAT05", "NO:NAVAT07")

    fun count(melding: String) {
        try {
            Metrics.counter("eessi_pensjon_pdl_produsent_identoppdatering", "melding", melding).increment()
        } catch (e: Exception) {
            logger.warn("Metrics feilet på enhet: $melding")
        }
    }

    fun sedHendelseMapping(hendelse: String): SedHendelse {
        val sedHendelseTemp = mapJsonToAny<SedHendelse>(hendelse)

        //støtte avsenderland SE i testmiljø Q2
        return if (profile != "prod" && profile != "integrationtest") {
            sedHendelseTemp.copy(avsenderLand = "SE", avsenderNavn = "SE:test")
        } else {
            sedHendelseTemp
        }
    }

}