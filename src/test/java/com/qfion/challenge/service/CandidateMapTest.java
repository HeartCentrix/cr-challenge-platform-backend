package com.qfion.challenge.service;

import com.qfion.challenge.controller.StatsController;
import com.qfion.challenge.repo.AttemptRepo;
import com.qfion.challenge.repo.CandidateRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CandidateMapTest {
    @Test void numericPublicAddressesAndProxyBoundaries() {
        for (String ip : List.of("127.0.0.1", "::1", "10.0.0.4", "192.168.1.1", "169.254.169.254", "100.64.1.1",
                "192.0.2.10", "198.51.100.1", "203.0.113.1", "2001:db8::1", "fc00::1", "example.com", "1234", "8.8.8.999"))
            assertFalse(ClientIpAddress.isPublic(ClientIpAddress.parse(ip)), ip);
        assertTrue(ClientIpAddress.isPublic(ClientIpAddress.parse("8.8.8.8")));
        assertTrue(ClientIpAddress.isPublic(ClientIpAddress.parse("2001:4860:4860::8888")));
        var request = new MockHttpServletRequest(); request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "1.1.1.1, 8.8.8.8, 54.1.2.3");
        assertEquals("127.0.0.1", ClientIpAddress.from(request, 0));
        assertEquals("8.8.8.8", ClientIpAddress.from(request, 2));
        request.removeHeader("X-Forwarded-For"); request.addHeader("X-Forwarded-For", "1.1.1.1");
        assertNull(ClientIpAddress.from(request, 2));
    }

    @SuppressWarnings("unchecked")
    @Test void groupsOnlyUsCandidatesAndCachesWithoutPublishingIps() throws Exception {
        var jdbc = mock(JdbcTemplate.class); var lookup = mock(IpLocationLookup.class);
        when(lookup.available()).thenReturn(true);
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of(
                new CandidateMapService.IpCount("8.8.8.8", 2), new CandidateMapService.IpCount("8.8.4.4", 1),
                new CandidateMapService.IpCount("1.1.1.1", 3), new CandidateMapService.IpCount("127.0.0.1", 1),
                new CandidateMapService.IpCount("alaska", 1)));
        when(lookup.lookup("8.8.8.8")).thenReturn(Optional.of(new IpLocationLookup.Location("US", 37.42, -122.08)));
        when(lookup.lookup("8.8.4.4")).thenReturn(Optional.of(new IpLocationLookup.Location("US", 37.43, -122.09)));
        when(lookup.lookup("1.1.1.1")).thenReturn(Optional.of(new IpLocationLookup.Location("AU", -33.9, 151.2)));
        when(lookup.lookup("alaska")).thenReturn(Optional.of(new IpLocationLookup.Location("US", 61.2, -149.9)));
        var service = new CandidateMapService(jdbc, lookup);
        assertEquals(new CandidateMapService.ActivityMap("ready", List.of(new CandidateMapService.Point(37.5, -122, 3))), service.snapshot());
        service.snapshot(); verify(jdbc, times(1)).query(anyString(), any(RowMapper.class));
        var attempts = mock(AttemptRepo.class); var candidates = mock(CandidateRepo.class);
        when(attempts.count()).thenReturn(12L); when(candidates.count()).thenReturn(8L);
        MockMvcBuilders.standaloneSetup(new StatsController(attempts, candidates, service)).build()
                .perform(get("/api/v1/stats")).andExpect(status().isOk()).andExpect(content().json("""
                    {"attempts":12,"developers":8,"activityMap":{"status":"ready","points":[{"latitude":37.5,"longitude":-122.0,"candidates":3}]}}
                    """, true));
    }

    @Test void missingDatabaseAndErrorsNeverInventDots() {
        var jdbc = mock(JdbcTemplate.class); var lookup = mock(IpLocationLookup.class);
        var service = new CandidateMapService(jdbc, lookup);
        assertEquals("unavailable", service.snapshot().status()); assertTrue(service.snapshot().points().isEmpty());
        verifyNoInteractions(jdbc);
        when(lookup.available()).thenReturn(true);
        when(jdbc.query(anyString(), any(RowMapper.class))).thenThrow(new IllegalStateException("internal details"));
        var failed = new CandidateMapService(jdbc, lookup);
        assertEquals("unavailable", failed.snapshot().status()); assertTrue(failed.snapshot().points().isEmpty());
    }

    @Test @EnabledIfEnvironmentVariable(named = "CHALLENGE_GEOIP_TESTS", matches = "true")
    void readsRealLocalDatabaseWithoutLookupNetworkRequests() throws Exception {
        var lookup = new IpLocationLookup("geoip/dbip-city-lite.mmdb"); lookup.open();
        try {
            assertTrue(lookup.available());
            assertEquals("US", lookup.lookup("8.8.8.8").orElseThrow().country());
            assertTrue(lookup.lookup("127.0.0.1").isEmpty()); assertTrue(lookup.lookup("example.com").isEmpty());
        } finally { lookup.close(); }
    }
}
