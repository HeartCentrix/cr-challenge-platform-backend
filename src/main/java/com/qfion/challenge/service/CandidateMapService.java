package com.qfion.challenge.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class CandidateMapService {
    private final JdbcTemplate jdbc;
    private final IpLocationLookup locations;
    private ActivityMap cached;
    private long expiresAt;
    public record Point(double latitude, double longitude, long candidates) {}
    public record ActivityMap(String status, List<Point> points) {}
    record IpCount(String ip, long count) {}
    private record Cell(double latitude, double longitude) {}

    /** At most one scan/lookup batch per five minutes per application instance. */
    public synchronized ActivityMap snapshot() {
        long now = System.nanoTime();
        if (cached != null && now < expiresAt) return cached;
        if (!locations.available()) return cache(new ActivityMap("unavailable", List.of()), now, 60);
        try {
            // Count each candidate once, using the IP from their latest submitted answer.
            var counts = jdbc.query("""
                    SELECT ip_address, count(*) AS candidates FROM (
                        SELECT DISTINCT ON (candidate_id) ip_address
                        FROM challenge_platform.attempt
                        ORDER BY candidate_id, submitted_at DESC, id DESC
                    ) latest GROUP BY ip_address
                    """, (rs, n) -> new IpCount(rs.getString("ip_address"), rs.getLong("candidates")));
            var cells = new HashMap<Cell, Long>();
            for (var count : counts) {
                locations.lookup(count.ip()).filter(location -> "US".equals(location.country())
                        && location.latitude() >= 24 && location.latitude() <= 50
                        && location.longitude() >= -125 && location.longitude() <= -66).ifPresent(location -> {
                    // Half-degree cells (~40-55 km), not precise IP-derived city coordinates.
                    var cell = new Cell(Math.round(location.latitude() * 2) / 2.0, Math.round(location.longitude() * 2) / 2.0);
                    cells.merge(cell, count.count(), Long::sum);
                });
            }
            var points = cells.entrySet().stream().map(entry -> new Point(entry.getKey().latitude(), entry.getKey().longitude(), entry.getValue()))
                    .sorted(Comparator.comparingDouble(Point::latitude).thenComparingDouble(Point::longitude)).toList();
            return cache(new ActivityMap("ready", points), now, 300);
        } catch (RuntimeException error) {
            log.warn("Candidate map aggregation unavailable; returning no location data.");
            return cache(new ActivityMap("unavailable", List.of()), now, 60);
        }
    }
    private ActivityMap cache(ActivityMap value, long now, int seconds) {
        cached = value; expiresAt = now + TimeUnit.SECONDS.toNanos(seconds); return value;
    }
}
