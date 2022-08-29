package no.nav.eessi.pensjon.pdl.adresseoppdatering

import no.nav.eessi.pensjon.personoppslag.pdl.model.AdressebeskyttelseGradering

fun isAdressebeskyttet(list: List<AdressebeskyttelseGradering>): Boolean {
    val beskyttetStatus = setOf(
        AdressebeskyttelseGradering.FORTROLIG,
        AdressebeskyttelseGradering.STRENGT_FORTROLIG
    )
    return (list intersect beskyttetStatus).isNotEmpty()
}
