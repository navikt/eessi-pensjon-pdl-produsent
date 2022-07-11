package no.nav.eessi.pensjon.eux

import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.eux.model.sed.P15000
import no.nav.eessi.pensjon.eux.model.sed.P4000
import no.nav.eessi.pensjon.eux.model.sed.P5000
import no.nav.eessi.pensjon.eux.model.sed.P6000
import no.nav.eessi.pensjon.eux.model.sed.P7000
import no.nav.eessi.pensjon.eux.model.sed.Person
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.personidentifisering.relasjoner.logger
import org.springframework.stereotype.Component

@Component
class UtenlandskPersonIdentifisering {

    fun hentAlleUtenlandskeIder(seder: List<Pair<ForenkletSED, SED>>): List<UtenlandskId> =
        seder
            .onEach { (forenkletSED, sed) -> logger.debug("sedType: ${forenkletSED.type}, SEDType: ${sed.type}, status: ${forenkletSED.status}") }
            .filter { (forenkletSED, _) -> forenkletSED.status == SedStatus.RECEIVED }
            .map { (_, sed ) -> sed }
            .flatMap { it.allePersoner() }
            .filter { it?.pin != null }
            .flatMap { it?.pin!! }
            .filter { it.land != null && it.identifikator != null }
            .filter { it.land != "NO" }
            .map { UtenlandskId(it.identifikator!!, it.land!!) }

}

data class UtenlandskId(val id: String, val land: String)

internal fun SED.allePersoner(): List<Person?> =
    listOf(
        nav?.bruker?.person,
        nav?.annenperson?.person,
        nav?.ektefelle?.person,
        nav?.verge?.person,
        nav?.ektefelle?.person,
        pensjon?.gjenlevende?.person
    ).plus((nav?.barn?.map { it.person } ?: emptyList())
    ).plus(
        when (type) {
            SedType.P4000 -> (this as P4000).p4000Pensjon?.gjenlevende?.person
            SedType.P5000 -> (this as P5000).p5000Pensjon?.gjenlevende?.person
            SedType.P6000 -> (this as P6000).p6000Pensjon?.gjenlevende?.person
            SedType.P7000 -> (this as P7000).p7000Pensjon?.gjenlevende?.person
            SedType.P15000 -> (this as P15000).p15000Pensjon?.gjenlevende?.person
            else -> null
        }
    ).filterNotNull().filter { it.pin != null }
