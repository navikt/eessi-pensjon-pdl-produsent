package no.nav.eessi.pensjon.eux

import no.nav.eessi.pensjon.eux.klient.EuxKlientLib
import no.nav.eessi.pensjon.eux.model.buc.BucMetadata
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class EuxService(
    private val euxKlient: EuxKlientLib,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {
    private var hentBuc: MetricsHelper.Metric
    private var hentSed: MetricsHelper.Metric
    private val secureLogger = LoggerFactory.getLogger("secureLog")

    init {
        hentSed = metricsHelper.init("hentSed", alert = MetricsHelper.Toggle.OFF)
        hentBuc = metricsHelper.init("hentBuc", alert = MetricsHelper.Toggle.OFF)
    }

    private val logger = LoggerFactory.getLogger(EuxService::class.java)


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
            val json = euxKlient.hentSedJson(rinaSakId, dokumentId).also { secureLogger.info("SED pre mapping:\n$it") }
            SED.fromJsonToConcrete(json)
        }
    }

    fun getBucMetadata(rinaSakId: String) : BucMetadata? {
        val metaData = euxKlient.hentBucJson(rinaSakId = rinaSakId)
        logger.debug("bucmetadata: ${metaData}")

        return metaData?.let { mapJsonToAny(it) }
    }

}
