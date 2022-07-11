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
            .flatMap { hentAllePersoner(it) }
            .filter { it?.pin != null }
            .flatMap { it?.pin!! }
            .filter { it.land != null && it.identifikator != null }
            .filter { it.land != "NO" }
            .map { UtenlandskId(it.identifikator!!, it.land!!) }


    internal fun hentAllePersoner(sed: SED): List<Person?> =
        listOf(
            sed.nav?.bruker?.person,
            sed.nav?.annenperson?.person,
            sed.nav?.ektefelle?.person,
            sed.nav?.verge?.person,
            sed.nav?.ektefelle?.person,
            sed.pensjon?.gjenlevende?.person
        ).plus((sed.nav?.barn?.map { it.person } ?: emptyList())
        ).plus(
            when (sed.type) {
                SedType.P4000 -> (sed as P4000).p4000Pensjon?.gjenlevende?.person
                SedType.P5000 -> (sed as P5000).p5000Pensjon?.gjenlevende?.person
                SedType.P6000 -> (sed as P6000).p6000Pensjon?.gjenlevende?.person
                SedType.P7000 -> (sed as P7000).p7000Pensjon?.gjenlevende?.person
                SedType.P15000 -> (sed as P15000).p15000Pensjon?.gjenlevende?.person
                else -> null
            }
        ).filterNotNull().filter { it.pin != null }
}

data class UtenlandskId(val id: String, val land: String)