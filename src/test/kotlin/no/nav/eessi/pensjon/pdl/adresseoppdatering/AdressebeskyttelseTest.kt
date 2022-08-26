package no.nav.eessi.pensjon.pdl.adresseoppdatering

import no.nav.eessi.pensjon.personoppslag.pdl.model.AdressebeskyttelseGradering
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdressebeskyttelseTest {

    @Test
    fun `Adressebeskyttet`() {
        assertTrue(isAdressebeskyttet(listOf(AdressebeskyttelseGradering.FORTROLIG)))
        assertTrue(isAdressebeskyttet(listOf(AdressebeskyttelseGradering.STRENGT_FORTROLIG)))
        assertTrue(isAdressebeskyttet(listOf(AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND)))
        assertTrue(isAdressebeskyttet(listOf(AdressebeskyttelseGradering.UGRADERT, AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND)))
    }

    @Test
    fun `Ikke adressebeskyttet`() {
        assertFalse(isAdressebeskyttet(listOf()))
        assertFalse(isAdressebeskyttet(listOf(AdressebeskyttelseGradering.UGRADERT)))
        assertFalse(isAdressebeskyttet(listOf(AdressebeskyttelseGradering.UGRADERT, AdressebeskyttelseGradering.UGRADERT)))
    }
}