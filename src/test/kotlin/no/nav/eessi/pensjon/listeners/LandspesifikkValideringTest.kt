package no.nav.eessi.pensjon.listeners

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class LandspesifikkValideringTest {

    private val valdidering = LandspesifikkValidering()

    @ParameterizedTest
    @CsvSource(
        "BEL, 770113-123-12, true",
        "BEL, 770113-12312, false",
        "BGR, 1234567890, true" ,
        "BGR, 123456789012, false" ,
        "BGR, 770113-12312, false",
        "FIN, 130177A636D, true",
        "FIN, 130177-308T, true",
        "FIN, 130177-5800, true",
        "FIN, 130177A5800, true",
        "FIN, 130177A636D, true",
        "ISL, 130177-7159, true",
        "ISL, 130177-71591, false",
        "ISL, 1301777159, false",
        "ISL, 1301777159-, false",
        "DNK, 130177-1234, true",
        "DNK, 130177-12341, false",
        "EST, 37701132722, true",
        "EST, 377A1132722, false",
        "LTU, 37701132722, true",
        "LTU, 377011-2722X, false",
        "ITA, PSNSVR77A13B123C, true",
        "ITA, a2c4e677A13B123C, true",
        "ITA, PSNSVR77A13B123C-, false",
        "ITA, PSNSVRA13B123C-, false",
        "ITA, PSNSVR%123C-, false",
        "LVA, 130177-18017, true",
        "LVA, 130177-180171, false",
        "LVA, 130177-1801Ø, false",
        "NLD, 1234.56.789, true",
        "NLD, 130177-1801Ø, false",
        "NLD, 130177&1801Ø, false",
        "NLD, 130-1801Ø, false",
        "POL, 77011312345, true",
        "POL, 770113123455, false",
        "POL, 77011312345%, false",
        "POL, 771---12345%, false",
        "XKZ, 7732423, false",
        "SVN, 1301771234567, true",
        "SVN, 1301771234567%, false",
        "SWE, 770113-1234, true",
        "SWE, 770113+1234, true",
        "SWE, 7701131+234, false",
        "DEU, 56 120157 F 016, true",
        "DEU, 02 140477 T 039, true",
        "DEU, 02 140477 T 03%, false",
        "HUN, 069-441-934, true",
        "HUN, 069-441-S34, false",
        "FRA, 2 52 01 75 068 079, true",
        "ESP, 0X5807635C, true",
        "GBR, ZX 91 67 77 C9, true"
    )
/*
    Tyskland: Eks. 56 120157 F 016, 02 140477 T 039.

    12 tegn, bokstaver og tall, mellomrom etter 2. og 8. tall, samt etter bokstav (bokstav er første i etternavn)
    Storbritannia: Eks. ZX 91 67 77 C9 tegn, bokstaver og tall, mellomrom etter 2. bokstav, samt etter hvert 2. tall.
    Ungarn: Eks. 069-441-934.
    9 tall, bindestrek mellom hver 3. tall.
    Frankrike: Eks. 2 52 01 75 068 079
    13 tall, mellomrom etter 1., 3., 5.,7., 10.
    Spania: Eks. 0X5807635C
    10 tegn, kun bokstaver og tall
*/

    fun `Gitt en UID som skal valideres mot landspesifikk formatering Så skal det retureres boolean`(land : String, uid : String, check: Boolean) {
        assertEquals(check, valdidering.validerLandsspesifikkUID(land, uid))
    }
}
