package no.nav.eessi.pensjon.pdl.filtrering

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.UtenlandskId
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endring
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endringstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Metadata
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskIdentifikasjonsnummer
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

internal class PdlFiltreringTest {

    private val kodeverkClient: KodeverkClient = mockk()

    val pdlFiltrering = PdlFiltrering(kodeverkClient)

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

        every { kodeverkClient.finnLandkode("SE") } returns "SWE"

        assertTrue(pdlFiltrering.finnesUidFraSedIPDL(utenlandskeIdentifikasjonsnummer, UtenlandskId("12345", "SWE")))
    }

    @Test
    fun `Gitt en uid fra sed ikke finnes i pdl så returnerer vi false`() {

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
            "123456",
            "SE",
            false,
            null,
            metadata))

        every { kodeverkClient.finnLandkode("SE") } returns "SWE"
        assertFalse(pdlFiltrering.finnesUidFraSedIPDL(utenlandskeIdentifikasjonsnummer, UtenlandskId("12345", "SWE")))
    }

    @Test
    fun `Gitt en uid fra sed og det ikke finnes noen uid i pdl så returnerer vi false`() {

        assertFalse(pdlFiltrering.finnesUidFraSedIPDL(emptyList(), UtenlandskId("12345", "SE")))
    }

    @Test
    fun `Gitt en svensk uid i sed og en svensk uid i pdl som ikke er like så opprettes det en oppgavemelding`() {

        val utenlandskIdSed = UtenlandskId("12345", "SE")

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

        val utenlandskIdPDL = listOf(
            UtenlandskIdentifikasjonsnummer(
                "12345678",
                "SWE",
                false,
                null,
                metadata
            )
        )

        every { kodeverkClient.finnLandkode("SWE") } returns "SE"
        assertTrue(pdlFiltrering.skalOppgaveOpprettes(utenlandskIdPDL, utenlandskIdSed))
    }

    @Test
    fun `Gitt en svensk uid i sed og en svensk uid i pdl som er like så opprettes det en oppgavemelding`() {

        val utenlandskIdSed = UtenlandskId("12345", "SE")

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
            "SWE",
            false,
            null,
            metadata))

        every { kodeverkClient.finnLandkode("SWE") } returns "SE"
        assertFalse(pdlFiltrering.skalOppgaveOpprettes(utenlandskeIdentifikasjonsnummer, utenlandskIdSed))
    }

}