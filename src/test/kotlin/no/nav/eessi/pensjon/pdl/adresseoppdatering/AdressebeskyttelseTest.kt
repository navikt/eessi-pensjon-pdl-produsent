package no.nav.eessi.pensjon.pdl.adresseoppdatering

import no.nav.eessi.pensjon.personoppslag.pdl.model.AdressebeskyttelseGradering.FORTROLIG
import no.nav.eessi.pensjon.personoppslag.pdl.model.AdressebeskyttelseGradering.STRENGT_FORTROLIG
import no.nav.eessi.pensjon.personoppslag.pdl.model.AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND
import no.nav.eessi.pensjon.personoppslag.pdl.model.AdressebeskyttelseGradering.UGRADERT
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdressebeskyttelseTest {

    @Test
    fun `Adressebeskyttet`() {
        assertTrue(isAdressebeskyttet(listOf(FORTROLIG)))
        assertTrue(isAdressebeskyttet(listOf(STRENGT_FORTROLIG)))
        assertTrue(isAdressebeskyttet(listOf(STRENGT_FORTROLIG_UTLAND, STRENGT_FORTROLIG)))
        assertTrue(isAdressebeskyttet(listOf(FORTROLIG, STRENGT_FORTROLIG_UTLAND)))
        assertTrue(isAdressebeskyttet(listOf(UGRADERT, STRENGT_FORTROLIG)))
        assertTrue(isAdressebeskyttet(listOf(UGRADERT, FORTROLIG)))
    }

    @Test
    fun `Ikke adressebeskyttet`() {
        assertFalse(isAdressebeskyttet(listOf()))
        assertFalse(isAdressebeskyttet(listOf(UGRADERT)))
        assertFalse(isAdressebeskyttet(listOf(STRENGT_FORTROLIG_UTLAND)))
        assertFalse(isAdressebeskyttet(listOf(UGRADERT, UGRADERT)))
    }
}