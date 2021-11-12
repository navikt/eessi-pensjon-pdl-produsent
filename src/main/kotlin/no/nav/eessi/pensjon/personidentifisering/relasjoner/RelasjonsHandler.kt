package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.personidentifisering.PersonIdentier
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object RelasjonsHandler {

    private val logger: Logger = LoggerFactory.getLogger(RelasjonsHandler::class.java)

    fun hentRelasjoner(sed: SED, rinaDocumentId: String, bucType: BucType): List<PersonIdentier> {
            try {
                getRelasjonHandler(sed, bucType, rinaDocumentId)?.let { handler ->
                    return filterRleasjoner(handler.hentRelasjoner())
                }
            } catch (ex: Exception) {
                logger.warn("Noe gikk galt under innlesing av fnr fra sed", ex)
            }
            return emptyList()
    }

    private fun filterRleasjoner(relasjonList: List<PersonIdentier>): List<PersonIdentier> {
         logger.debug("*** Filterer relasjonListe, samme oppføringer, ufyldige verdier o.l")

        relasjonList.onEach { logger.debug("$it") }

        //filterering av relasjoner med kjent fnr
        val relasjonerMedFnr = relasjonList.filter { it.fnr != null }.distinctBy { it.fnr }
        //filtering av relasjoner uten kjent fnr
        val relasjonerUtenFnr = relasjonList.filter { it.fnr == null }

        return (relasjonerMedFnr + relasjonerUtenFnr).also { logger.debug("$it") }

    }

    private fun getRelasjonHandler(sed: SED, bucType: BucType, rinaDocumentId: String): AbstractRelasjon? {

        return when (sed.type) {
            //R005 SED eneste vi leter etter fnr for R_BUC_02
            SedType.R005 -> R005Relasjon(sed, bucType,rinaDocumentId)

            //Øvrige P-SED vi støtter for innhenting av FNR
            SedType.P2000 -> P2000Relasjon(sed, bucType,rinaDocumentId)
            SedType.P2200 -> P2200Relasjon(sed, bucType,rinaDocumentId)
            SedType.P2100 -> P2100Relasjon(sed, bucType,rinaDocumentId)

            SedType.P5000 -> P5000Relasjon(sed, bucType, rinaDocumentId)
            SedType.P6000 -> P6000Relasjon(sed, bucType,rinaDocumentId)
            SedType.P8000 -> P8000AndP10000Relasjon(sed, bucType,rinaDocumentId)
            SedType.P10000 -> P8000AndP10000Relasjon(sed, bucType,rinaDocumentId)
            SedType.P15000 -> P15000Relasjon(sed, bucType,rinaDocumentId)

            //H-SED vi støtter for innhenting av fnr kun for forsikret
            SedType.H070, SedType.H120, SedType.H121 -> GenericRelasjon(sed, bucType, rinaDocumentId)

            //resternede gyldige sed med fnr kommer hit.. (P9000, P3000, P4000.. osv.)
            else -> GenericRelasjon(sed, bucType,rinaDocumentId)
        }

    }

}
