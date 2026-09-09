package com.qfion.challenge.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CandidateRegionTest {
    @Test void resolvesStateCodesAndDbipNamesWithoutInferringMissingRegions() {
        assertEquals("TX",IpLocationLookup.regionFromRecord(Map.of("country",Map.of("iso_code","US"),
            "subdivisions",List.of(Map.of("iso_code","TX")))));
        assertEquals("CA",IpLocationLookup.regionFromRecord(Map.of("country",Map.of("iso_code","US"),
            "subdivisions",List.of(Map.of("names",Map.of("en","California"))))));
        assertEquals("DC",CandidateRegions.stateCode("US-DC",null));
        assertEquals("NON_US",IpLocationLookup.regionFromRecord(Map.of("country",Map.of("iso_code","IN"))));
        assertEquals("UNKNOWN",IpLocationLookup.regionFromRecord(Map.of("country",Map.of("iso_code","US"))));
        assertEquals("UNKNOWN",IpLocationLookup.regionFromRecord(null));
        assertFalse(CandidateRegions.valid("ZZ")); assertTrue(CandidateRegions.valid("UNKNOWN"));
    }
    @Test @EnabledIfEnvironmentVariable(named="CHALLENGE_GEOIP_TESTS",matches="true")
    void usesInstalledOfflineDatabaseAndNeverLocatesPrivateAddresses() throws Exception {
        var lookup=new IpLocationLookup("geoip/dbip-city-lite.mmdb");
        lookup.open();
        try {
            assertTrue(lookup.available());
            assertEquals("UNKNOWN",lookup.region("127.0.0.1"));
            assertEquals("UNKNOWN",lookup.region("::1"));
            assertEquals("UNKNOWN",lookup.region("not-an-ip"));
            String region=lookup.region("8.8.8.8");
            assertTrue(CandidateRegions.STATES.containsKey(region),"The installed DB should resolve this public US test address to a state");
        } finally {lookup.close();}
    }
}
