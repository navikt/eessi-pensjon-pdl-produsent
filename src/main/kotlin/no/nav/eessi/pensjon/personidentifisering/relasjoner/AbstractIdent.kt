package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.personidentifisering.PersonIdentier
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter

val logger: Logger = LoggerFactory.getLogger(AbstractIdent::class.java)

abstract class AbstractIdent() {
//    private val sed: SED, private val bucType: BucType, private val rinaDocumentId: String
//    val forsikretPerson = sed.nav?.bruker?.person

    abstract fun hentRelasjoner(sed: SED): List<PersonIdentier>

    fun hentForsikretPerson(sed: SED): List<PersonIdentier> {
        val forsikretPerson = sed.nav?.bruker?.person
        logger.info("Leter etter gyldig ident og relasjon(er) i SedType: ${sed.type}")

        forsikretPerson?.let { person ->
            val fodselnummer = Fodselsnummer.fra(person.pin?.firstOrNull { it.land == "NO" }?.identifikator)
            val pinItemUtlandList = person.pin?.filterNot { it.land == "NO" }

            logger.debug("Legger til person forsikret og sedType: ${sed.type}, fnr: $fodselnummer, uid: $pinItemUtlandList")
            return listOf(
                PersonIdentier(
                    fodselnummer, pinItemUtlandList, sedType = sed.type
                )
            )
        }

        logger.warn("Ingen forsikret person funnet")
        return emptyList()
    }

    fun mapFdatoTilLocalDate(fdato: String?) : LocalDate? = fdato?.let { LocalDate.parse(it, DateTimeFormatter.ISO_DATE) }

    fun bestemSaktype(bucType: BucType): Saktype? {
        return when(bucType) {
            BucType.P_BUC_01 -> Saktype.ALDER
            BucType.P_BUC_02 -> Saktype.GJENLEV
            BucType.P_BUC_03 -> Saktype.UFOREP
            else -> null
        }
    }

}
