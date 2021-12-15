package no.nav.eessi.pensjon.pdl.validering

import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.personidentifisering.PersonIdenter
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse

internal class PdlValideringTest {

    @Test
    fun `Gitt en ny uid naar avsenderland og uidland ikke er det samme saa returner true`() {
        val pdlValidering = PdlValidering()
        val identifisertPerson = IdentifisertPerson(
            PersonIdenter(
                Fodselsnummer.fra("11067122781")
            ), emptyList()
        )
        assertTrue(pdlValidering.erUidLandAnnetEnnAvsenderLand(listOf(identifisertPerson), "SE"))
    }

    @Test
    fun `Gitt en ny uid naar avsenderland og uidland er det samme saa returner false`() {
        val pdlValidering = PdlValidering()
        val identifisertPerson = IdentifisertPerson(
            PersonIdenter(
                Fodselsnummer.fra("11067122781")
            ), emptyList()
        )
        assertFalse (pdlValidering.erUidLandAnnetEnnAvsenderLand(listOf(identifisertPerson), "DE"))
    }
}