package no.nav.eessi.pensjon.pdl.identoppdatering

import io.micrometer.core.instrument.Metrics
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.oppgave.OppgaveHandler
import no.nav.eessi.pensjon.pdl.OppgaveModel
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.retry.RetryCallback
import org.springframework.retry.RetryContext
import org.springframework.retry.RetryListener
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException

@Service
class SedHendelseIdentBehandler(
    private val vurderIdentoppdatering: VurderIdentoppdatering,
    private val personMottakKlient: PersonMottakKlient,
    private val oppgaveHandler: OppgaveHandler,
    @Value("\${SPRING_PROFILES_ACTIVE:}") private val profile: String
) : OppgaveModel() {
    private val logger = LoggerFactory.getLogger(SedHendelseIdentBehandler::class.java)
    private val secureLogger = LoggerFactory.getLogger("secureLog")

    @Retryable( // Vi gjør retry når det er lås på PDL-objektet - gjøres langt opp i stacken for at vi skal gjøre nytt oppslag mot PDL
            include = [HttpClientErrorException::class],
            exceptionExpression = "statusCode.value == 423",
            backoff = Backoff(delayExpression = "@sedHendelseIdentBehandlerRetryConfig.initialRetryMillis", maxDelay = 200000L, multiplier = 3.0),
            listeners  = ["sedHendelseIdentBehandlerRetryLogger"]
    )
    fun behandle(hendelse: String) {
        logger.debug(hendelse)
        logger.debug("Profile: $profile")
        val sedHendelse = sedHendelseMapping(hendelse).also { secureLogger.debug("Sedhendelse:\n${it.toJson()}") }

        if (testHendelseIProd(sedHendelse)) {
            logger.error("Avsender id er ${sedHendelse.avsenderId}. Dette er testdata i produksjon!!!\n$sedHendelse")
            return
        }

        logger.info("*** Starter pdl endringsmelding (IDENT) prosess for BucType: ${sedHendelse.bucType}, SED: ${sedHendelse.sedType}, RinaSakID: ${sedHendelse.rinaSakId} ***")

        val result = vurderIdentoppdatering.vurderUtenlandskIdent(sedHendelse)

        log(result)

        when (result) {
            is Oppdatering -> {
                personMottakKlient.opprettPersonopplysning(result.pdlEndringsOpplysninger)
            }
            is Oppgave -> {
                oppgaveHandler.opprettOppgave(result.oppgaveData)
            }

            else -> {
                /* NO-OP */
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

@Profile("!retryConfigOverride")
@Component
data class SedHendelseIdentBehandlerRetryConfig(val initialRetryMillis: Long = 20000L)

@Component
class SedHendelseIdentBehandlerRetryLogger : RetryListener {
    private val logger = LoggerFactory.getLogger(SedHendelseIdentBehandlerRetryLogger::class.java)
    override fun <T : Any?, E : Throwable?> onError(context: RetryContext?, callback: RetryCallback<T, E>?, throwable: Throwable?) {
        logger.error("Feil under behandling av sedHendelse - try #${context?.retryCount } - ${throwable?.toString()}", throwable)
    }
}