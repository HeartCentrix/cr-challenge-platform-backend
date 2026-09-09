package com.qfion.challenge.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CandidateRegionService {
    private final JdbcTemplate jdbc;
    private final IpLocationLookup locations;
    @Value("${challenge.regions.backfill-enabled:true}") private boolean backfillEnabled;

    public String resolve(String ip) {
        try { return locations.region(ip); }
        catch (RuntimeException error) { return null; } // Location failure must never prevent an answer.
    }
    @Scheduled(initialDelay=5000,fixedDelay=60000)
    @Transactional
    public void backfill() {
        if (!backfillEnabled || !locations.available()) return;
        // Bounded, indexed batches; no historical IP is sent off the backend.
        var rows = jdbc.queryForList("SELECT id,ip_address FROM challenge_platform.attempt WHERE region_code IS NULL ORDER BY id FOR UPDATE SKIP LOCKED LIMIT 200");
        for (var row : rows) {
            String code = resolve((String)row.get("ip_address"));
            if (code != null) jdbc.update("UPDATE challenge_platform.attempt SET region_code=? WHERE id=? AND region_code IS NULL",code,row.get("id"));
        }
    }
}
