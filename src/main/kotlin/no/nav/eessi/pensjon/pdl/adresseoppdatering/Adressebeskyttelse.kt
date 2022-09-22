package no.nav.eessi.pensjon.pdl.adresseoppdatering

import no.nav.eessi.pensjon.personoppslag.pdl.model.AdressebeskyttelseGradering

fun erUtenAdressebeskyttelse(graderinger: List<AdressebeskyttelseGradering>) =
    (setOf(
        AdressebeskyttelseGradering.FORTROLIG,
        AdressebeskyttelseGradering.STRENGT_FORTROLIG
    ) intersect graderinger)
        .isEmpty()
