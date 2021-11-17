package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.personidentifisering.PersonIdenter
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object RelasjonsHandler {

    private val logger: Logger = LoggerFactory.getLogger(RelasjonsHandler::class.java)

    fun hentRelasjoner(sed: SED, rinaDocumentId: String, bucType: BucType): List<PersonIdenter> {
        try {
            getRelasjonHandler(sed, bucType, rinaDocumentId).let { handler ->
                logger.debug("Benytter følgende handler: ${handler.javaClass.simpleName}")
                return filterRleasjoner(handler.hentRelasjoner(sed))
            }
        } catch (ex: Exception) {
            logger.warn("Noe gikk galt under innlesing av fnr fra sed", ex)
        }
        return emptyList()
    }

    private fun filterRleasjoner(relasjonList: List<PersonIdenter>): List<PersonIdenter> {
        logger.debug("*** Filterer relasjonListe, samme oppføringer, ufyldige verdier o.l")

        relasjonList.onEach { logger.debug("$it") }

        //filterering av relasjoner med kjent fnr
        val relasjonerMedFnr = relasjonList.filter { it.fnr != null }.distinctBy { it.fnr }
        //filtering av relasjoner uten kjent fnr
        val relasjonerUtenFnr = relasjonList.filter { it.fnr == null }

        return (relasjonerMedFnr + relasjonerUtenFnr).also { logger.debug("$it") }
    }

    private fun getRelasjonHandler(sed: SED, bucType: BucType, rinaDocumentId: String): AbstractIdent {

        return when (sed.type) {
            //R005 SED eneste vi leter etter fnr for R_BUC_02
            SedType.R005 -> R005Ident()

            //Øvrige P-SED vi støtter for innhenting av FNR
            SedType.P2000 -> P2000Ident()
            SedType.P2200 -> P2200Ident()
            SedType.P2100 -> P2100Ident()
            SedType.P5000 -> P5000Ident()
            SedType.P6000 -> P6000Ident()

            SedType.P8000 -> P8000AndP10000Ident()
            SedType.P10000 -> P8000AndP10000Ident()
            SedType.P15000 -> P15000Ident()

            //H-SED vi støtter for innhenting av fnr kun for forsikret
            SedType.H070, SedType.H120, SedType.H121 -> GenericIdent()

            //resternede gyldige sed med fnr kommer hit.. (P9000, P3000, P4000.. osv.)
            else -> GenericIdent()
        }
    }
}
