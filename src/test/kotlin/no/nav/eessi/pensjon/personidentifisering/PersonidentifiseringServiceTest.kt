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
    fun `Gitt ident har uid fra SED som ikke finnes i PDL Så nytt ident med kun uid fra SED`() {
//        val identPerson = IdentifisertPerson(
//            PersonIdenter(
//                Fodselsnummer.fra("11067122781")
//            ), listOf(
//                UtenlandskIdentifikasjonsnummer("1234567891236540", "SWE", true, metadata = Metadata(emptyList<Endring>(), false, "FREG", "321654")),
//                UtenlandskIdentifikasjonsnummer("521552123456", "SWE", false, metadata = Metadata(emptyList<Endring>(), false, "FREG", "321654"))
//            )
//        )
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
        val listeOverutenlandskId = UtenlandskId("11067122781", "SWE")

        every { kodeverkClient.finnLandkode("SE") } returns "SWE"
        every { kodeverkClient.finnLandkode("DK") } returns "DKK"

        val newIdent = pdlFiltrering.finnesUidFraSedIPDL(listeOverutenlandskIdentifikasjonsnummer, listeOverutenlandskId)
//        val newIdent = pdlFiltrering.filtrerUidSomIkkeFinnesIPdl(identPerson, kodeverkClient, "enFinInstitusjon")

        assertTrue(newIdent)
//        assertEquals(1, newIdent?.personIdenterFraSed?.uid?.size)
//        assertEquals(true, newIdent?.uidFraPdl?.isEmpty())
    }

    @Test
    fun `Gitt UIDer fra sed ikke finens når filtrering av UID duplikater utføres Så retureres null`() {
//        val identPerson = IdentifisertPerson(
//            PersonIdenter(
//                Fodselsnummer.fra("11067122781")
//            ), listOf(
//                UtenlandskIdentifikasjonsnummer("1234567891236540", "SWE", true, metadata = Metadata(emptyList<Endring>(), false, "FREG", "321654")),
//                UtenlandskIdentifikasjonsnummer("521552123456", "SWE", false, metadata = Metadata(emptyList<Endring>(), false, "FREG", "321654"))
//            )
//        )
//
//        every { kodeverkClient.finnLandkode("SE") } returns "SWE"
//        every { kodeverkClient.finnLandkode("DK") } returns "DKK"
//
//        val newIdent = pdlFiltrering.filtrerUidSomIkkeFinnesIPdl(identPerson, kodeverkClient, "enFinInstitusjon")
//
//        assertNull(newIdent)

        val utenlandskIdNr = listOf(
                UtenlandskIdentifikasjonsnummer("1234567891236540", "SWE", true, metadata = Metadata(emptyList<Endring>(), false, "FREG", "321654")),
                UtenlandskIdentifikasjonsnummer("521552123456", "SWE", false, metadata = Metadata(emptyList<Endring>(), false, "FREG", "321654")))

        val utenlandskId = UtenlandskId("11067122781", "SWE")

        val newIdent = pdlFiltrering.finnesUidFraSedIPDL(utenlandskIdNr, utenlandskId)

        assertTrue(newIdent)

    }

    @Test
    fun `Gitt UIDer fra sed som ikke finnes i PDL når filtering av UID duplikater utføres Så returneres nytt personIdent`() {
//        val identPerson = IdentifisertPerson(
//            PersonIdenter(
//                Fodselsnummer.fra("11067122781")
//            ),
//            emptyList()
//        )
//
//        every { kodeverkClient.finnLandkode("SE") } returns "SWE"
//        every { kodeverkClient.finnLandkode("DK") } returns "DKK"
//
//        val newIdent = pdlFiltrering.filtrerUidSomIkkeFinnesIPdl(identPerson, kodeverkClient, "enFinInstitusjon")
//
//        assertEquals(3, newIdent?.personIdenterFraSed?.uid?.size)
//        assertEquals(true, newIdent?.uidFraPdl?.isEmpty())

        val utenlandskIdNr = emptyList<UtenlandskIdentifikasjonsnummer>()
        val utenlandskId =

        val newIdent = pdlFiltrering.finnesUidFraSedIPDL(utenlandskIdNr, utenlandskId)


    }

    @Test
    fun `Gitt UIDer fra SED som finnes i PDL når filtrering av UID duplikater utføres Så returneres null`() {
        val identPerson = IdentifisertPerson(
            PersonIdenter(
                Fodselsnummer.fra("11067122781")
            ), listOf(
                UtenlandskIdentifikasjonsnummer("1234567891236540", "SWE", true, metadata = Metadata(emptyList<Endring>(), false, "FREG", "321654")),
                UtenlandskIdentifikasjonsnummer("521552123456", "SWE", false, metadata = Metadata(emptyList<Endring>(), false, "FREG", "321654"))
            )
        )

        every { kodeverkClient.finnLandkode("SE") } returns "SWE"
        every { kodeverkClient.finnLandkode("DK") } returns "DKK"

        val newIdent = pdlFiltrering.filtrerUidSomIkkeFinnesIPdl(identPerson, kodeverkClient, "enFinInstitusjon")
        assertNull(newIdent)

    }

}