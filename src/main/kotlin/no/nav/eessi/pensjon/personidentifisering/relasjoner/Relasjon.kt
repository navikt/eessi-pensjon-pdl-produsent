package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import no.nav.eessi.pensjon.personidentifisering.SEDPersonRelasjon
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter

val logger: Logger = LoggerFactory.getLogger(Relasjon::class.java)

abstract class AbstractRelasjon(private val sed: SED, private val bucType: BucType, private val rinaDocumentId: String) {

    val forsikretPerson = sed.nav?.bruker?.person

    abstract fun hentRelasjoner(): List<SEDPersonRelasjon>

//    abstract fun hentIdenter(sed: SED): List<SEDPersonRelasjon>

    fun hentForsikretPerson(): List<SEDPersonRelasjon> {
        logger.info("Leter etter gyldig ident og relasjon(er) i SedType: ${sed.type}")

        forsikretPerson?.let { person ->
            val fodselnummer = Fodselsnummer.fra(person.pin?.firstOrNull { it.land == "NO" }?.identifikator)
            val pinItemUtlandList = person.pin?.filterNot { it.land == "NO" }

            logger.debug("Legger til person ${Relasjon.FORSIKRET} og sedType: ${sed.type}")
            return listOf(
                SEDPersonRelasjon(
                    fodselnummer,
                    pinItemUtlandList,
                    Relasjon.FORSIKRET,
                    sedType = sed.type
                )
            )
        }

        logger.warn("Ingen forsikret person funnet")
        throw RuntimeException("Ingen forsikret person funnet")
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
