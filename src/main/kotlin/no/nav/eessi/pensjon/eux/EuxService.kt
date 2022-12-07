package no.nav.eessi.pensjon.eux

import no.nav.eessi.pensjon.eux.klient.EuxKlient
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.erGyldig
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import javax.annotation.PostConstruct

@Service
class EuxService(
    private val euxKlient: EuxKlient,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {
    private val logger = LoggerFactory.getLogger(EuxService::class.java)

    private lateinit var hentBuc: MetricsHelper.Metric
    private lateinit var hentSed: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
        hentSed = metricsHelper.init("hentSed", alert = MetricsHelper.Toggle.OFF)
        hentBuc = metricsHelper.init("hentBuc", alert = MetricsHelper.Toggle.OFF)
    }

    /**
     * Henter SED fra Rina EUX API.
     *
     * @param rinaSakId: Hvilken Rina-sak SED skal hentes fra.
     * @param dokumentId: Hvilket SED-dokument som skal hentes fra spesifisert sak.
     *
     * @return Objekt av type <T : Any> som spesifisert i param typeRef.
     */
    fun hentSed(rinaSakId: String, dokumentId: String): SED {
        return hentSed.measure {
            val json = euxKlient.hentSedJson(rinaSakId, dokumentId)
            SED.fromJsonToConcrete(json)
        }
    }

    /**
     * Henter Buc fra Rina.
     */
    fun hentBuc(rinaSakId: String): Buc =
        hentBuc.measure {
            euxKlient.hentBuc(rinaSakId) ?: throw RuntimeException("Ingen BUC")
        }

    fun alleGyldigeSEDForBuc(rinaSakId: String): List<Pair<ForenkletSED, SED>> = // TODO Hvorfor henter vi "alle"?
        (hentBuc(rinaSakId).documents ?: emptyList())
            .filter { it.id != null }
            .map { ForenkletSED(it.id!!, it.type, SedStatus.fra(it.status)) }
            .filter { it.harGyldigStatus() }  // TODO "Gyldig" ? - skal vi ikke bare vurdere innkomne?
            .filter { it.type.erGyldig() } // TODO - logikken her er kopiert fra Journalføring(?) - er det rett?
            .also { logger.info("Fant ${it.size} dokumenter i BUC: $it") }
            .map { forenkletSED -> Pair(forenkletSED, hentSed(rinaSakId, forenkletSED.id)) }
            .onEach { (forenkletSED, _) -> logger.debug("SED av type: ${forenkletSED.type}, status: ${forenkletSED.status}") }

}
