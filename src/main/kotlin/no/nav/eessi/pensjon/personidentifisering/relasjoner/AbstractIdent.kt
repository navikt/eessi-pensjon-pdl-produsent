package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val logger: Logger = LoggerFactory.getLogger(AbstractIdent::class.java)

abstract class AbstractIdent() {

    abstract fun hentRelasjoner(sed: SED): List<Fodselsnummer?>

}
