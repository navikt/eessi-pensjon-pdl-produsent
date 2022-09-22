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
        assertFalse(erUtenAdressebeskyttelse(listOf(FORTROLIG)))
        assertFalse(erUtenAdressebeskyttelse(listOf(STRENGT_FORTROLIG)))
        assertFalse(erUtenAdressebeskyttelse(listOf(STRENGT_FORTROLIG_UTLAND, STRENGT_FORTROLIG)))
        assertFalse(erUtenAdressebeskyttelse(listOf(FORTROLIG, STRENGT_FORTROLIG_UTLAND)))
        assertFalse(erUtenAdressebeskyttelse(listOf(UGRADERT, STRENGT_FORTROLIG)))
        assertFalse(erUtenAdressebeskyttelse(listOf(UGRADERT, FORTROLIG)))
    }

    @Test
    fun `Ikke adressebeskyttet`() {
        assertTrue(erUtenAdressebeskyttelse(listOf()))
        assertTrue(erUtenAdressebeskyttelse(listOf(UGRADERT)))
        assertTrue(erUtenAdressebeskyttelse(listOf(STRENGT_FORTROLIG_UTLAND)))
        assertTrue(erUtenAdressebeskyttelse(listOf(UGRADERT, UGRADERT)))
    }
}