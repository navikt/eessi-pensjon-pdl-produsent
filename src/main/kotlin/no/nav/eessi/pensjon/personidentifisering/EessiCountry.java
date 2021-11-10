package no.nav.eessi.pensjon.personidentifisering;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public enum EessiCountry {
    AUSTRIA("AT", "AUT"),
    BELGIUM("BE", "BEL"),
    BULGARIA("BG", "BGR"),
    CROATIA("HR", "HRV"),
    CYPRUS("CY", "CYP"),
    CZECH_REPUBLIC("CZ", "CZE"),
    DENMARK("DK", "DNK"),
    ESTONIA("EE", "EST"),
    FINLAND("FI", "FIN"),
    FRANCE("FR", "FRA"),
    GERMANY("DE", "DEU"),
    GREECE("GR", "GRC"),
    HUNGARY("HU", "HUN"),
    ICELAND("IS", "ISL"),
    IRELAND("IE", "IRL"),
    ITALY("IT", "ITA"),
    LATVIA("LV", "LVA"),
    LIECHTENSTEIN("LI", "LIE"),
    LITHUANIA("LT", "LTU"),
    LUXEMBOURG("LU", "LUX"),
    MALTA("MT", "MLT"),
    NETHERLANDS("NL", "NLD"),
    NORWAY("NO", "NOR"),
    POLAND("PL", "POL"),
    PORTUGAL("PT", "PRT"),
    ROMANIA("RO", "ROU"),
    SLOVAKIA("SK", "SVK"),
    SLOVENIA("SI", "SVN"),
    SPAIN("ES", "ESP"),
    SWEDEN("SE", "SWE"),
    SWITZERLAND("CH", "CHE"),
    UNITED_KINGDOM("GB", "GBR");
    
    private final String alpha2;
    private final String alpha3;
    
    private static final Map<String, EessiCountry> alpha2ToCountryMap;
    private static final Map<String, EessiCountry> alpha3ToCountryMap;
    
    static {
        alpha2ToCountryMap = Arrays.stream(EessiCountry.values()).collect(Collectors.toMap(EessiCountry::getAlpha2, c-> c));
        alpha3ToCountryMap = Arrays.stream(EessiCountry.values()).collect(Collectors.toMap(EessiCountry::getAlpha3, c-> c));
    }
    
    EessiCountry(String alpha2, String alpha3) {
        this.alpha2 = alpha2;
        this.alpha3 = alpha3;
        
    }

    public String getAlpha2() {
        return alpha2;
    }

    public String getAlpha3() {
        return alpha3;
    }
    
    public static Optional<EessiCountry> mapFromAlpha2(String alpha2) {
        if ("UK".equals(alpha2)) {
            return Optional.of(UNITED_KINGDOM);
        }
        if ("EL".equals(alpha2)) {
            return Optional.of(GREECE);
        }
        return Optional.ofNullable(alpha2ToCountryMap.get(alpha2));
    } 

    public static Optional<EessiCountry> mapFromAlpha3(String alpha3) {
        return Optional.ofNullable(alpha3ToCountryMap.get(alpha3));
    } 
    
}
