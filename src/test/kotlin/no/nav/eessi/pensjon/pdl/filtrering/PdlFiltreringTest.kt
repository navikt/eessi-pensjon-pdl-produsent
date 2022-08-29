package no.nav.eessi.pensjon.pdl.filtrering

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.UtenlandskId
import no.nav.eessi.pensjon.eux.model.sed.Adresse
import no.nav.eessi.pensjon.klienter.kodeverk.KodeverkClient
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endring
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endringstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Metadata
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskAdresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskIdentifikasjonsnummer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
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


        val utenlandskeIdentifikasjonsnummer = listOf(UtenlandskIdentifikasjonsnummer(
            "12345",
            "SWE",
            false,
            null,
            mockMeta()))

        every { kodeverkClient.finnLandkode("SWE") } returns "SE"
        assertFalse(pdlFiltrering.skalOppgaveOpprettes(utenlandskeIdentifikasjonsnummer, utenlandskIdSed))
    }


    @ParameterizedTest
    @CsvSource(
        "195402021234, 540202-1234, true",
        "195402021234, 440332-2333, false"
    )
    fun `Gitt en uid fra PDL som sjekkes mot SED uid fra SE`(pdluid: String, seduid: String, validate: Boolean) {
        assertEquals(validate, pdlFiltrering.sjekkForSverigeIdFraPDL(pdluid, seduid))
    }

    @ParameterizedTest
    @CsvSource(
        "195402021234, 540202-1234, true",
        "195402021234, 440332-2333, true"
    )
    fun `Gitt en svenskUID i PDL er forskjellig fra uid i SED Men er faktisk samme ident Så skal det ikke opprettes oppgave`(pdlId: String, sedId: String, validate: Boolean) {
        every { kodeverkClient.finnLandkode("SWE") } returns "SE"
        val pdluid = listOf(UtenlandskIdentifikasjonsnummer(
            pdlId,
            "SWE",
            false,
            null,
            mockMeta()))

        val seduid = UtenlandskId(sedId, "SE")
        assertEquals(validate, pdlFiltrering.skalOppgaveOpprettes(pdluid, seduid))
    }

    @ParameterizedTest
    @CsvSource(
        "195402021234, 540202-1234, false",
        "195402021234, 440332-2333, true"
    )
    fun `Gitt en svenskUID fra PDL Saa sjekkes det ytterligere om id er identisk med SEDuid`(pdlId: String, sedId: String, validate: Boolean) {
        every { kodeverkClient.finnLandkode("SWE") } returns "SE"
        val pdluid = listOf(UtenlandskIdentifikasjonsnummer(
            pdlId,
            "SWE",
            false,
            null,
            mockMeta()))

        val seduid = UtenlandskId(sedId, "SE")
        assertEquals(validate, pdlFiltrering.sjekkYterligerePaaPDLuidMotSedUid (pdluid, seduid))
    }

    @Test
    fun `Gitt en utlandsadresse i SED saa sjekker vi om den finnes i PDL`(){
        every { kodeverkClient.finnLandkode("SE") } returns "SWE"

        val adresse = Adresse(
            gate = "EddyRoad",
            bygning = "EddyHouse",
            by = "EddyCity",
            postnummer = "111",
            region = "Oslo",
            land ="SWE",
            kontaktpersonadresse = null,
        )
        val utenlandskAdresse = UtenlandskAdresse(
            adressenavnNummer = adresse.gate,
            landkode = "SE",
            postkode = adresse.postnummer,
            bySted = adresse.by,
            bygningEtasjeLeilighet  = adresse.bygning,
            regionDistriktOmraade = adresse.region
        )
        assertTrue(pdlFiltrering.isUtenlandskAdresseISEDMatchMedAdresseIPDL(adresse, utenlandskAdresse))
    }


    private fun mockMeta() = Metadata(
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


}