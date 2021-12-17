package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.Institusjon
import no.nav.eessi.pensjon.eux.model.sed.Person
import no.nav.eessi.pensjon.eux.model.sed.PinItem
import no.nav.eessi.pensjon.personidentifisering.Rolle
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class UtlandMappingTest() {

    companion object {
        const val SLAPP_SKILPADDE = "09035225916"
    }

    @Test
    fun `Mapping av utlandpin skal gi en list som ikke innneholder personer som har NO som land`() {
        val person : Person = createPersonForUtland(SLAPP_SKILPADDE)
        val pinItems =  UtlandMapping().mapUtenlandsPin(person)

        assert(pinItems.size == 2)
        assertEquals(1, pinItems.filterNot { it.utstederland == "DE" }.size)
        assertEquals(1, pinItems.filterNot { it.utstederland == "SE" }.size)
    }

    private fun createPersonForUtland(fnr: String?, rolle: Rolle? = null): Person {
        return Person(
            rolle = rolle?.name,
            foedselsdato = Fodselsnummer.fra(fnr)?.getBirthDateAsIso() ?: "1955-09-12",
            pin = listOfNotNull(
                PinItem(land = "DE", identifikator = "1234567121", institusjon =  Institusjon(institusjonsnavn = "NAVDE", institusjonsid = "123")),
                PinItem(land = "SE", identifikator = "1234567111", institusjon =  Institusjon(institusjonsnavn = "NAVSE", institusjonsid = "456")),
                PinItem(land = "NO", identifikator = "12345673343", institusjon =  Institusjon(institusjonsnavn = "NAVNO", institusjonsid = "789"))
            )
        )
    }
}


