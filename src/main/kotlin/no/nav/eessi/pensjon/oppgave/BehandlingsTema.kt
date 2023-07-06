package no.nav.eessi.pensjon.oppgave

import com.fasterxml.jackson.annotation.JsonValue
import no.nav.eessi.pensjon.pdl.validering.GyldigeLand

enum class Behandlingstema(@JsonValue val kode: String) {
    GJENLEVENDEPENSJON("ab0011"),
    ALDERSPENSJON("ab0254"),
    UFOREPENSJON("ab0194"),
    BARNEP("ab0255"),
    TILBAKEBETALING("ab0007");
    companion object {
        fun hentKode(kode: String): Behandlingstema? = Behandlingstema.values().firstOrNull { it.kode == kode }
    }
}