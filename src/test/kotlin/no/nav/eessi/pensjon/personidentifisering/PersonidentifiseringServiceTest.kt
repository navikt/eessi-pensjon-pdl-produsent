package no.nav.eessi.pensjon.personidentifisering

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.Endring
import no.nav.eessi.pensjon.personoppslag.pdl.model.Metadata
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskIdentifikasjonsnummer
import no.nav.eessi.pensjon.services.kodeverk.KodeverkClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PersonidentifiseringServiceTest {

    private val kodeverkClient: KodeverkClient = mockk(relaxed = true)
    private val personService: PersonService = mockk()
    private lateinit var personidentifiseringService: PersonidentifiseringService


    @BeforeEach
    fun setUp() {
        personidentifiseringService = PersonidentifiseringService(personService, kodeverkClient)
    }


    @Test
    fun `Gitt ident har uid fra SED som ikke finnes i PDL Så nytt ident med kun uid fra SED`() {
        val identPerson = IdentifisertPerson(
            PersonIdenter(
                Fodselsnummer.fra("11067122781"), listOf(
                    UtenlandskPin("P2000", "521552123456", "SE"),
                    UtenlandskPin("P2000", "1234567891236540", "SE"),
                    UtenlandskPin("P2000", "13451345234542", "DK")
                )
            ), listOf(
                UtenlandskIdentifikasjonsnummer("1234567891236540", "SWE", true, metadata = Metadata(emptyList<Endring>(), false, "FREG", "321654")),
                UtenlandskIdentifikasjonsnummer("521552123456", "SWE", false, metadata = Metadata(emptyList<Endring>(), false, "FREG", "321654"))
            )
        )

        every { kodeverkClient.finnLandkode("SE") } returns "SWE"
        every { kodeverkClient.finnLandkode("DK") } returns "DKK"

        val newIdent = personidentifiseringService.filtrerUidSomIkkeFinnesIPdl(identPerson)

        assertEquals(1, newIdent?.personIdenterFraSed?.uid?.size)
        assertEquals(true, newIdent?.uidFraPdl?.isEmpty())
    }

    @Test
    fun `Gitt ident har ingen uid fra SED new ident som er null`() {
        val identPerson = IdentifisertPerson(
            PersonIdenter(
                Fodselsnummer.fra("11067122781"), emptyList()
            ), listOf(
                UtenlandskIdentifikasjonsnummer("1234567891236540", "SWE", true, metadata = Metadata(emptyList<Endring>(), false, "FREG", "321654")),
                UtenlandskIdentifikasjonsnummer("521552123456", "SWE", false, metadata = Metadata(emptyList<Endring>(), false, "FREG", "321654"))
            )
        )

        every { kodeverkClient.finnLandkode("SE") } returns "SWE"
        every { kodeverkClient.finnLandkode("DK") } returns "DKK"

        val newIdent = personidentifiseringService.filtrerUidSomIkkeFinnesIPdl(identPerson)

        assertNull(newIdent)
    }

    @Test
    fun `Gitt ident har uid fra SED som ikke finnes i PDL ingen uid i PDL Så nytt ident med kun uid fra SED`() {
        val identPerson = IdentifisertPerson(
            PersonIdenter(
                Fodselsnummer.fra("11067122781"), listOf(
                    UtenlandskPin("P2000", "521552123456", "SE"),
                    UtenlandskPin("P2000", "1234567891236540", "SE"),
                    UtenlandskPin("P2000", "13451345234542", "DK")
                )
            ),
            emptyList()
        )

        every { kodeverkClient.finnLandkode("SE") } returns "SWE"
        every { kodeverkClient.finnLandkode("DK") } returns "DKK"

        val newIdent = personidentifiseringService.filtrerUidSomIkkeFinnesIPdl(identPerson)

        assertEquals(3, newIdent?.personIdenterFraSed?.uid?.size)
        assertEquals(true, newIdent?.uidFraPdl?.isEmpty())

        println(newIdent)
    }

    @Test
    fun `Gitt UIDer fra SED som finnes i PDL når filtrering av UID duplikater utføres Så returneres null`() {
        val identPerson = IdentifisertPerson(
            PersonIdenter(
                Fodselsnummer.fra("11067122781"), listOf(
                    UtenlandskPin("P2000", "521552123456", "SE"),
                    UtenlandskPin("P2000", "1234567891236540", "SE"),
                )
            ), listOf(
                UtenlandskIdentifikasjonsnummer("1234567891236540", "SWE", true, metadata = Metadata(emptyList<Endring>(), false, "FREG", "321654")),
                UtenlandskIdentifikasjonsnummer("521552123456", "SWE", false, metadata = Metadata(emptyList<Endring>(), false, "FREG", "321654"))
            )
        )

        every { kodeverkClient.finnLandkode("SE") } returns "SWE"
        every { kodeverkClient.finnLandkode("DK") } returns "DKK"

        val newIdent = personidentifiseringService.filtrerUidSomIkkeFinnesIPdl(identPerson)
        assertNull(newIdent)

    }

}