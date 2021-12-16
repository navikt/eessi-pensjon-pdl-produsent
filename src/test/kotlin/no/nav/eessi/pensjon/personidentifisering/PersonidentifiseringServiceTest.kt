package no.nav.eessi.pensjon.personidentifisering

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.UtenlandskId
import no.nav.eessi.pensjon.pdl.filtrering.PdlFiltrering
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endring
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endringstype
import no.nav.eessi.pensjon.personoppslag.pdl.model.Metadata
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskIdentifikasjonsnummer
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersonidentifiseringServiceTest {

    private val kodeverkClient: KodeverkClient = mockk(relaxed = true)
    private val personService: PersonService = mockk()
    private lateinit var personidentifiseringService: PersonidentifiseringService
    private val pdlFiltrering: PdlFiltrering = PdlFiltrering(kodeverkClient)


    @BeforeEach
    fun setUp() {
        personidentifiseringService = PersonidentifiseringService(personService, kodeverkClient)
    }


    @Test
    fun `Gitt en svensk uid i Sed når uid ikke finnes i PDL Så returner false`() {

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

        val listeOverutenlandskIdentifikasjonsnummer = listOf(UtenlandskIdentifikasjonsnummer(
                "11067122781",
                "SWE",
                false,
                null,
                metadata
            )
        )

        val listeOverutenlandskId = UtenlandskId("110671227812", "SWE")
        every { kodeverkClient.finnLandkode("SE") } returns "SWE"

        assertFalse(pdlFiltrering.finnesUidFraSedIPDL(listeOverutenlandskIdentifikasjonsnummer, listeOverutenlandskId))

    }

    @Test
    fun `Gitt en svensk uid i Sed når uid finnes i PDL Så returner true`() {

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

        val utenlandskIdentifikasjonsnummer = listOf(UtenlandskIdentifikasjonsnummer(
            "11067122781",
            "SE",
            false,
            null,
            metadata
        ))

        val uid = UtenlandskId("11067122781","SWE")
        every { kodeverkClient.finnLandkode("SE") } returns "SWE"

        assertTrue(pdlFiltrering.finnesUidFraSedIPDL(utenlandskIdentifikasjonsnummer, uid))
    }

}