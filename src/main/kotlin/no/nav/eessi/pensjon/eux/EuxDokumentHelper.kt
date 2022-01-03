package no.nav.eessi.pensjon.eux

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
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
class EuxDokumentHelper(
    private val euxKlient: EuxKlient,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())
) {

    private val logger = LoggerFactory.getLogger(EuxDokumentHelper::class.java)

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

    fun hentAlleSedIBuc(rinaSakId: String, documents: List<ForenkletSED>): List<Pair<ForenkletSED, SED>> {
        return documents
            .filter(ForenkletSED::harGyldigStatus)
            .map { docitem -> Pair(docitem, hentSed(rinaSakId, docitem.id)) }
            .onEach { (docitem, sed) ->  logger.debug("SED av type: ${docitem.type}, status: ${docitem.status}") }

    }

    fun hentAlleGyldigeDokumenter(buc: Buc): List<ForenkletSED> {
        return hentBucDokumenter(buc)
            .filter { it.type.erGyldig() }
            .also { logger.info("Fant ${it.size} dokumenter i BUC: $it") }
    }

    fun hentAlleDocumenter(buc: Buc): List<ForenkletSED> {
        return hentBucDokumenter(buc)
    }

    /**
     * Henter Buc fra Rina.
     */
    fun hentBuc(rinaSakId: String): Buc {
        return hentBuc.measure {
            euxKlient.hentBuc(rinaSakId) ?: throw RuntimeException("Ingen BUC")
        }
    }

    /**
     * Henter alle dokumenter (SEDer) i en Buc.
     */
    fun hentBucDokumenter(buc: Buc): List<ForenkletSED> {
        val documents = buc.documents ?: return emptyList()
        return documents
            .filter { it.id != null }
            .map { ForenkletSED(it.id!!, it.type, SedStatus.fra(it.status)) }
            .filter { it.harGyldigStatus() }
    }

}
