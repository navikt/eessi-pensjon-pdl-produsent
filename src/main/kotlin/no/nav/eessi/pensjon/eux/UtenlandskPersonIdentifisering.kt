package no.nav.eessi.pensjon.eux

import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.eux.model.sed.SED
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object UtenlandskPersonIdentifisering {

    private val logger: Logger by lazy { LoggerFactory.getLogger(UtenlandskPersonIdentifisering::class.java) }

    fun finnAlleUtenlandskeIDerIMottatteSed(seder: List<Pair<ForenkletSED, SED>>): List<UtenlandskId> =
        seder
            .onEach { (forenkletSED, sed) -> logger.debug("sedType: ${forenkletSED.type}, SEDType: ${sed.type}, status: ${forenkletSED.status}") }
            .filter { (forenkletSED, _) -> forenkletSED.status == SedStatus.RECEIVED }
            .map { (_, sed ) -> sed }
            .flatMap { it.allePersoner() }
            .filter { it.pin != null }
            .flatMap { it.pin!! }
            .filter { it.land != null && it.identifikator != null }
            .filter { it.land != "NO" }
            .map { UtenlandskId(it.identifikator!!, it.land!!) }

}

data class UtenlandskId(val id: String, val land: String)
