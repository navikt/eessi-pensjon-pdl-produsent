package no.nav.eessi.pensjon.pdl.adresseoppdatering

import no.nav.eessi.pensjon.personoppslag.pdl.model.AdressebeskyttelseGradering

fun isAdressebeskyttet(list: List<AdressebeskyttelseGradering>): Boolean {
    val beskyttetStatus = setOf(
        AdressebeskyttelseGradering.FORTROLIG,
        AdressebeskyttelseGradering.STRENGT_FORTROLIG,
        AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND
    )
    return (list intersect beskyttetStatus).isNotEmpty()
}
