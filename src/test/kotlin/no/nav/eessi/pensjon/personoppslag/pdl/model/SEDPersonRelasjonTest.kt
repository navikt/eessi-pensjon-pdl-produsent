package no.nav.eessi.pensjon.personoppslag.pdl.model

import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

private const val FNR = "11067122781"
class SEDPersonRelasjonTest {

    @BeforeEach
    fun setUp() {
    }

    @Test
    fun isFnrDnrSinFdatoLikSedFdato() {
        var sedPersonRelasjon = SEDPersonRelasjon(
            fnr = Fodselsnummer.fra(FNR),
            relasjon = Relasjon.FORSIKRET,
            fdato = LocalDate.now(),
            rinaDocumentId = ""
        )
        assertEquals(sedPersonRelasjon.isFnrDnrSinFdatoLikSedFdato(),false)

        sedPersonRelasjon = sedPersonRelasjon.copy(fdato = LocalDate.of(1971, 6, 11))
        assertEquals(sedPersonRelasjon.isFnrDnrSinFdatoLikSedFdato(),true)
    }
}