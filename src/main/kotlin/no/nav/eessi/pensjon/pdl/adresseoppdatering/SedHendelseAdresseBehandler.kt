package no.nav.eessi.pensjon.pdl.adresseoppdatering

import io.micrometer.core.instrument.Metrics
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.pdl.OppgaveModel
import no.nav.eessi.pensjon.pdl.PersonMottakKlient
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
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
class SedHendelseBehandler(
    private val adresseoppdatering: VurderAdresseoppdatering,
    private val personMottakKlient: PersonMottakKlient,
    @Value("\${SPRING_PROFILES_ACTIVE:}") private val profile: String
) : OppgaveModel() {
    private val logger = LoggerFactory.getLogger(SedHendelseBehandler::class.java)
    private val secureLogger = LoggerFactory.getLogger("secureLog")

    @Retryable( // Vi gjør retry når det er lås på PDL-objektet - gjøres langt opp i stacken for at vi skal gjøre nytt oppslag mot PDL
        include = [HttpClientErrorException::class],
        exceptionExpression = "statusCode.value == 423",
        backoff = Backoff(delayExpression = "@sedHendelseBehandlerRetryConfig.initialRetryMillis", maxDelay = 200000L, multiplier = 3.0),
        listeners  = ["sedHendelseBehandlerRetryLogger"]
    )
    fun behandle(hendelse: String) {
        val sedHendelse = mapJsonToAny<SedHendelse>(hendelse)

        if (testDataInProd(sedHendelse)) {
            logger.error("Avsender id er ${sedHendelse.avsenderId}. Dette er testdata i produksjon!!!\n$sedHendelse")
            return
        }

        logger.info("*** Starter pdl endringsmelding (ADRESSE) prosess for BucType: ${sedHendelse.bucType}, SED: ${sedHendelse.sedType}, RinaSakID: ${sedHendelse.rinaSakId} ***")

        val result = adresseoppdatering.vurderUtenlandskKontaktadresse(sedHendelse)

        log(result)

        if (result is Oppdatering) {
            personMottakKlient.opprettPersonopplysning(result.pdlEndringsOpplysninger)
        }

        countForAddress(result.metricTagValue)
    }

    private fun countForAddress(melding: String) {
        try {
            Metrics.counter("eessi_pensjon_pdl_produsent_adresseoppdatering", "melding", melding).increment()
        } catch (e: Exception) {
            logger.warn("Metrics feilet med melding: $melding", e)
        }
    }

    private fun log(result: Result) {
        logger.info(Fodselsnummer.vaskFnr(result.toString()))
        if (result is Oppdatering) {
            secureLogger.info("Oppdatering til PDL:\n${result.pdlEndringsOpplysninger.toJson()}")
        }
    }

    private fun testDataInProd(sedHendelse: SedHendelse) =
        profile == "prod" && sedHendelse.avsenderId in listOf("NO:NAVAT05", "NO:NAVAT07")

}

// Dette er gjort slik for at vi skal kunne overstyre tiden mellom retries i tester
@Profile("!retryConfigOverride")
@Component
data class SedHendelseBehandlerRetryConfig(val initialRetryMillis: Long = 20000L)

@Component
class SedHendelseBehandlerRetryLogger : RetryListener {
    private val logger = LoggerFactory.getLogger(SedHendelseBehandlerRetryLogger::class.java)
    override fun <T : Any?, E : Throwable?> onError(context: RetryContext?, callback: RetryCallback<T, E>?, throwable: Throwable?) {
        logger.warn("Feil under behandling av sedHendelse - try #${context?.retryCount } - ${throwable?.toString()}", throwable)
    }
}