package no.nav.eessi.pensjon.pdl.validering

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.kodeverk.KodeverkClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource


/**
 * Lenke til landsspesifikk valideringsregler:
 * https://confluence.adeo.no/display/MOFO/OMR-321+Utenlandsk+ID+-+6+-+Produsent
 */
internal class LandspesifikkValideringTest {

    var kodeverkClient: KodeverkClient = mockk(relaxed = true)
    private val valdidering = LandspesifikkValidering(kodeverkClient)

    @BeforeEach
    fun setup() {
        every { kodeverkClient.finnLandkode("PL") } returns "POL"
        every { kodeverkClient.finnLandkode("SE") } returns "SWE"
        every { kodeverkClient.finnLandkode("BE") } returns "BEL"
        every { kodeverkClient.finnLandkode("BR") } returns "BGR"
        every { kodeverkClient.finnLandkode("FI") } returns "FIN"
        every { kodeverkClient.finnLandkode("IS") } returns "ISL"
        every { kodeverkClient.finnLandkode("DK") } returns "DNK"
        every { kodeverkClient.finnLandkode("ET") } returns "EST"
        every { kodeverkClient.finnLandkode("LT") } returns "LTU"
        every { kodeverkClient.finnLandkode("IT") } returns "ITA"
        every { kodeverkClient.finnLandkode("LV") } returns "LVA"
        every { kodeverkClient.finnLandkode("NL") } returns "NLD"
        every { kodeverkClient.finnLandkode("PL") } returns "POL"
        every { kodeverkClient.finnLandkode("SV") } returns "SVN"
        every { kodeverkClient.finnLandkode("FR") } returns "FRA"
        every { kodeverkClient.finnLandkode("GB") } returns "GBR"
        every { kodeverkClient.finnLandkode("ES") } returns "ESP"
        every { kodeverkClient.finnLandkode("HU") } returns "HUN"
        every { kodeverkClient.finnLandkode("DE") } returns "DEU"

    }

    @ParameterizedTest
    @CsvSource(
        "BE, 770113-123-12, true",
        "BE, 770113-12312, false",
        "BR, 1234567890, true" ,
        "BR, 123456789012, false" ,
        "BR, 770113-12312, false",
        "FI, 130177A636D, true",
        "FI, 130177-308T, true",
        "FI, 130177-5800, true",
        "FI, 130177A5800, true",
        "FI, 130177A636D, true",
        "IS, 130177-7159, true",
        "IS, 130177-71591, false",
        "IS, 1301777159, false",
        "IS, 1301777159-, false",
        "DK, 130177-1234, true",
        "DK, 130177-12341, false",
        "ET, 37701132722, true",
        "ET, 377A1132722, false",
        "LT, 37701132722, true",
        "LT, 377011-2722X, false",
        "IT, PSNSVR77A13B123C, true",
        "IT, a2c4e677A13B123C, true",
        "IT, PSNSVR77A13B123C-, false",
        "IT, PSNSVRA13B123C-, false",
        "IT, PSNSVR%123C-, false",
        "LV, 130177-18017, true",
        "LV, 130177-180171, false",
        "LV, 130177-1801Ø, false",
        "NL, 1234.56.789, true",
        "NL, 130177-1801Ø, false",
        "NL, 130177&1801Ø, false",
        "NL, 130-1801Ø, false",
        "PL, 77011312345, true",
        "PL, 770113123455, false",
        "PL, 77011312345%, false",
        "PL, 771---12345%, false",
        "XKZ, 7732423, false",
        "SV, 1301771234567, true",
        "SV, 1301771234567%, false",
        "SE, 770113-1234, true",
        "SE, 19542020-1234, true",
        "SE, 1954202012, true",
        "SE, 7701131111-1234, false",
        "SE, 7701131+234, false",
        "SE, 7701131234, true",
        "SE, 7 7 0 1131234, true",
        "SE, 7 7% 1131234, false",
        "SE, -, false",
        "DE, 56 120157 F 016, true",
        "DE, 02 140477 T 039, true",
        "DE, 02 140477 T 03%, false",
        "HU, 069-441-934, true",
        "HU, 069-441-S34, false",
        "FR, 2 52 01 75 068 079, true",
        "FR, 2 52 01 75 068 079A, false",
        "ES, 0X5807635C, true",
        "ES, 0X5807635C%, false",
        "ES, 0X580763 5C, false",
        "GB, ZX 91 67 77 C9, true",
        "GB, ZX 91-67 77MØ, false",
        "GB, ZX 91-6777M, false",
        "GB, ZXS, false", //format og lengde
        "GB, ZX, false" //length
    )
/**
 * Regler for andre land vi kommuniserer med

 * Tyskland: Eks. 56 120157 F 016, 02 140477 T 039.
 * 12 tegn, bokstaver og tall, mellomrom etter 2. og 8. tall, samt etter bokstav (bokstav er første i etternavn)

 * Storbritannia: Eks. ZX 91 67 77 C9 tegn, bokstaver og tall, mellomrom etter 2. bokstav, samt etter hvert 2. tall.

 * Ungarn: Eks. 069-441-934.
 * 9 tall, bindestrek mellom hver 3. tall.
 * Frankrike: Eks. 2 52 01 75 068 079
 * 13 tall, mellomrom etter 1., 3., 5.,7., 10.
 * Spania: Eks. 0X5807635C
 * 10 tegn, kun bokstaver og tall
*/

    fun `Gitt en UID som skal valideres mot landspesifikk formatering Så skal det retureres boolean`(land : String, uid : String, check: Boolean) {
        assertEquals(check, valdidering.validerLandsspesifikkUID(land, uid))
    }
}
