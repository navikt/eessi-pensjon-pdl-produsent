package no.nav.eessi.pensjon.pdl.filtrering

import no.nav.eessi.pensjon.personoppslag.pdl.model.Endring
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endringstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Metadata
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskIdentifikasjonsnummer
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

internal class PdlFiltreringTest {

    val pdlFiltrering = PdlFiltrering()

    @Test
    fun`Gitt en UID som finnes i PDL når det sjekkes om UID finnes i PDL så returner true`() {

        val metadata = Metadata(
            listOf(
                Endring(
                    "kilde",
                    LocalDateTime.now(),
                    "ole",
                    "system1",
                    Endringstype.OPPRETT
                )
            ),
            false,
            "nav",
            "1234"
        )

        val utenlandskeIdentifikasjonsnummer = listOf(UtenlandskIdentifikasjonsnummer(
            "12345",
        "SE",
        false,
        null,
        metadata))

        assertTrue(pdlFiltrering.finnesUidIPdl(utenlandskeIdentifikasjonsnummer, "12345"))
    }
}