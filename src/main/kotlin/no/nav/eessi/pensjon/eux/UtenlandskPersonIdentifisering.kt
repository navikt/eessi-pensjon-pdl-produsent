package no.nav.eessi.pensjon.eux

import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.eux.model.sed.P15000
import no.nav.eessi.pensjon.eux.model.sed.P15000Pensjon
import no.nav.eessi.pensjon.eux.model.sed.P4000
import no.nav.eessi.pensjon.eux.model.sed.P4000Pensjon
import no.nav.eessi.pensjon.eux.model.sed.P5000
import no.nav.eessi.pensjon.eux.model.sed.P5000Pensjon
import no.nav.eessi.pensjon.eux.model.sed.P6000
import no.nav.eessi.pensjon.eux.model.sed.P6000Pensjon
import no.nav.eessi.pensjon.eux.model.sed.P7000
import no.nav.eessi.pensjon.eux.model.sed.P7000Pensjon
import no.nav.eessi.pensjon.eux.model.sed.Person
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.personidentifisering.relasjoner.logger
import org.springframework.stereotype.Component

@Component
class UtenlandskPersonIdentifisering {

    fun hentAllePersoner(sed: SED): List<Person?> =
        listOf(
            sed.nav?.bruker?.person,
            sed.nav?.annenperson?.person,
            sed.nav?.ektefelle?.person,
            sed.nav?.verge?.person,
            sed.nav?.ektefelle?.person,
            sed.pensjon?.gjenlevende?.person
        ).plus((sed.nav?.barn?.map { it.person } ?: emptyList())
        ).plus(
                when(sed.type) {
                    SedType.P4000 -> hentP4000Personer((sed as P4000).p4000Pensjon)
                    SedType.P5000 -> hentP5000Personer((sed as P5000).p5000Pensjon)
                    SedType.P6000 -> hentP6000Personer((sed as P6000).p6000Pensjon)
                    SedType.P7000 -> hentP7000Personer((sed as P7000).p7000Pensjon)
                    SedType.P15000 -> hentP15000Personer((sed as P15000).p15000Pensjon)
                    else -> null
                }
        ).filterNotNull().filter { it.pin != null }


    fun filterKunPaaSedStatus(forenkletSED: ForenkletSED, sed: SED) : List<Person?> {
        logger.debug("sedType: ${forenkletSED.type}, SEDType: ${sed.type}, status: ${forenkletSED.status}")

        return if (forenkletSED.status == SedStatus.RECEIVED)
            hentAllePersoner(sed)
        else {
            logger.debug("Ikke ${SedStatus.RECEIVED}")
            emptyList()
        }
    }

    fun filtrerAlleUtenlandskeIder(personer: List<Person?>): List<UtenlandskId> {
        return personer.filter { person -> person?.pin != null }
            .flatMap { person -> person?.pin!! }
            .filter { pin -> pin.land != null && pin.identifikator != null }
            .filter { pin -> pin.land != "NO" }
            .map { pin -> UtenlandskId(pin.identifikator!!, pin.land!!) }
    }

    private fun hentAlleUtenlandskeIder(doc: ForenkletSED, sed: SED): List<UtenlandskId> = filtrerAlleUtenlandskeIder(filterKunPaaSedStatus(doc, sed))
    fun hentAlleUtenlandskeIder(seder: List<Pair<ForenkletSED, SED>>): List<UtenlandskId> = seder.flatMap { (docitem, sed) -> hentAlleUtenlandskeIder(docitem, sed) }

    private fun hentP4000Personer(p4000Pensjon: P4000Pensjon?): Person? = p4000Pensjon?.gjenlevende?.person
    private fun hentP5000Personer(p5000Pensjon: P5000Pensjon?): Person? = p5000Pensjon?.gjenlevende?.person
    private fun hentP6000Personer(p6000Pensjon: P6000Pensjon?): Person? = p6000Pensjon?.gjenlevende?.person
    private fun hentP7000Personer(p7000Pensjon: P7000Pensjon?): Person? = p7000Pensjon?.gjenlevende?.person
    private fun hentP15000Personer(p15000Pensjon: P15000Pensjon?): Person? = p15000Pensjon?.gjenlevende?.person

}

data class UtenlandskId(val id: String, val land: String)