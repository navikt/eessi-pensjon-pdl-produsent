package no.nav.eessi.pensjon.pdl.validering

import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class PdlValideringTest {

    @Test
    fun `Gitt en ny uid naar avsenderland og uidland er det samme saa returner false`() {

        val pdlValidering = PdlValidering()
        val listeOverIdentifisertePersoner = listOf(IdentifisertPerson())


    }


}