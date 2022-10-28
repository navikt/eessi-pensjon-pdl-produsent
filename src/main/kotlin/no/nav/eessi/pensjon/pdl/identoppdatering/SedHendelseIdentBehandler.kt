package no.nav.eessi.pensjon.pdl.identoppdatering

import io.micrometer.core.instrument.Metrics
import no.nav.eessi.pensjon.models.SedHendelse
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.retry.RetryCallback
import org.springframework.retry.RetryContext
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.retry.listener.RetryListenerSupport
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException

@Service
class SedHendelseIdentBehandler(
        private val identOppdatering: IdentOppdatering,
        private val personMottakKlient: PersonMottakKlient,
        @Value("\${SPRING_PROFILES_ACTIVE:}") private val profile: String
) {
    private val logger = LoggerFactory.getLogger(SedHendelseIdentBehandler::class.java)
    private val secureLogger = LoggerFactory.getLogger("secureLog")

    @Retryable( // Vi gjør retry når det er lås på PDL-objektet - gjøres langt opp i stacken for at vi skal gjøre nytt oppslag mot PDL
            include = [HttpClientErrorException::class],
            exceptionExpression = "statusCode.value == 423",
            backoff = Backoff(delayExpression = "@identBehandlerRetryConfig.initialRetryMillis", maxDelay = 200000L, multiplier = 3.0),
            listeners  = ["behandleIdentRetryLogger"]
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

        val result = identOppdatering.oppdaterUtenlandskIdent(sedHendelse)

        log(result)
        if (result is IdentOppdatering.Update) {
            personMottakKlient.opprettPersonopplysning(result.pdlEndringsOpplysninger)
        }

        count(result.metricTagValue)
    }

    private fun log(result: IdentOppdatering.Result) {
        when (result) {
            is IdentOppdatering.Update -> {
                secureLogger.debug("Update:\n${result.toJson()}")
                logger.info("Update(${result.description}")
            }

            is IdentOppdatering.NoUpdate -> {
                logger.info(result.toString())
            }
        }
    }

    private fun testHendelseIProd(sedHendelse: SedHendelse) =
            profile == "prod" && sedHendelse.avsenderId in listOf("NO:NAVAT05", "NO:NAVAT07")

    fun count(melding: String) {
        try {
            Metrics.counter("PDLIdentOppdateringResultat",   "melding", melding).increment()
        } catch (e: Exception) {
            logger.warn("Metrics feilet på enhet: $melding")
        }
    }

    fun sedHendelseMapping(hendelse: String): SedHendelse {
        val sedHendelseTemp = SedHendelse.fromJson(hendelse)

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
data class IdentBehandlerRetryConfig(val initialRetryMillis: Long = 20000L)

@Component
class BehandleIdentRetryLogger : RetryListenerSupport() {
    private val logger = LoggerFactory.getLogger(BehandleIdentRetryLogger::class.java)
    override fun <T : Any?, E : Throwable?> onError(context: RetryContext?, callback: RetryCallback<T, E>?, throwable: Throwable?) {
        logger.warn("Feil under behandling av sedHendelse - try #${context?.retryCount } - ${throwable?.toString()}", throwable)
    }
}