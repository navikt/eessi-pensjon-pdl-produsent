package no.nav.eessi.pensjon.eux

import no.nav.eessi.pensjon.eux.model.sed.*
import org.springframework.stereotype.Component

@Component
class UtenlandskPersonIdentifisering {
    fun hentAllePersoner(sed: SED): List<Person> {
        val personer: List<Person>  = mutableListOf()
        personer.plus(sed.nav?.bruker?.person)
        personer.plus(sed.nav?.annenperson?.person)
        personer.plus(sed.nav?.ektefelle?.person)
        personer.plus(sed.nav?.verge?.person)
        personer.plus(sed.nav?.ektefelle?.person)
        personer.plus(sed.nav?.barn?.map { barn -> barn.person })
        personer.plus(sed.pensjon?.gjenlevende?.person)


        when(sed.type) {
            SedType.P4000 -> personer.plus(hentP4000Personer(sed as P4000Pensjon))
            SedType.P5000 -> personer.plus(hentP5000Personer(sed as P5000Pensjon))
            SedType.P6000 -> personer.plus(hentP6000Personer(sed as P6000Pensjon))
            SedType.P7000 -> personer.plus(hentP7000Personer(sed as P7000Pensjon))
            SedType.P15000 -> personer.plus(hentP15000Personer(sed as P15000Pensjon))
            else -> {}
        }
        return personer
    }

    fun filtrerAlleUtenlandskeIder(personer: List<Person>): List<UtenlandskId> {
        return personer.filter { person -> person.pin != null }
            .flatMap { person -> person.pin!! }
            .filter { pin -> pin.land != null && pin.identifikator != null }
            .filter { pin -> pin.land != "NO" }
            .map { pin -> UtenlandskId(pin.identifikator!!, pin.land!!) }
    }

    fun hentAlleUtenlandskeIder(sed: SED): List<UtenlandskId> = filtrerAlleUtenlandskeIder(hentAllePersoner(sed))
    fun hentAlleUtenlandskeIder(seder: List<SED>): List<UtenlandskId> = seder.flatMap { sed -> hentAlleUtenlandskeIder(sed) }

    private fun hentP4000Personer(p4000Pensjon: P4000Pensjon): Person? = p4000Pensjon.gjenlevende?.person
    private fun hentP5000Personer(p5000Pensjon: P5000Pensjon): Person? = p5000Pensjon.gjenlevende?.person
    private fun hentP6000Personer(p6000Pensjon: P6000Pensjon): Person? = p6000Pensjon.gjenlevende?.person
    private fun hentP7000Personer(p7000Pensjon: P7000Pensjon): List<Person?> = listOf(p7000Pensjon.gjenlevende?.person, p7000Pensjon.bruker?.person)
    private fun hentP15000Personer(p15000Pensjon: P15000Pensjon): Person? = p15000Pensjon.gjenlevende?.person

}


data class UtenlandskId(val id: String, val land: String)