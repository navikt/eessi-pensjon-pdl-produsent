package no.nav.eessi.pensjon.pdl.validering

import io.mockk.mockk
import no.nav.eessi.pensjon.eux.UtenlandskId
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import no.nav.eessi.pensjon.personidentifisering.SEDPersonRelasjon
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endring
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endringstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Metadata
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskIdentifikasjonsnummer
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertFalse

internal class PdlValideringTest {
    private val kodeverkClient = mockk<KodeverkClient>(relaxed = true)

    private val pdlValidering = PdlValidering(kodeverkClient)

    @Test
    fun `Gitt en ny uid naar avsenderland og uidland ikke er det samme saa returner true`() {

        val identifisertPerson = UtenlandskId("11067122781", "DE")
        assertTrue(pdlValidering.erUidLandAnnetEnnAvsenderLand(identifisertPerson, "SE"))
    }

    @Test
    fun `Gitt en ny uid naar avsenderland og uidland er det samme saa returner false`() {

        val identifisertPerson = UtenlandskId("11067122781", "DE")
        assertFalse (pdlValidering.erUidLandAnnetEnnAvsenderLand(identifisertPerson, "DE"))
    }

    @Test
    fun `Gitt at vi ikke har en identifisert person så returnerer vi false `() {
        assertFalse (pdlValidering.finnesIdentifisertePersoner(emptyList()))
    }

    @Test
    fun `Gitt at vi har en identifisert person så returnerer vi true `() {

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

        val identifisertPerson = listOf(IdentifisertPerson(
            Fodselsnummer.fra("11067122781"),
            listOf(
                UtenlandskIdentifikasjonsnummer(
                    "321654687", "DE", false, null, metadata
                )
            ),
            "32165498765",
            "NO",
            "0328",
            false,
            null,
            SEDPersonRelasjon(Fodselsnummer.fra("11067122781"), Relasjon.FORSIKRET, rinaDocumentId =  "3123123")

        ))

        assertTrue(pdlValidering.finnesIdentifisertePersoner(identifisertPerson))
    }

}