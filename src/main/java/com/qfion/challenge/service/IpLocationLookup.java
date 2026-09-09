package com.qfion.challenge.service;

import com.maxmind.db.Reader;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/** Local MMDB lookup. No candidate IP is sent to a third-party service. */
@Service
@Slf4j
public class IpLocationLookup {
    private final Path database;
    private Reader reader;
    public record Location(String country, double latitude, double longitude) {}

    public IpLocationLookup(@Value("${challenge.geoip.database:geoip/dbip-city-lite.mmdb}") String database) {
        this.database = Path.of(database);
    }
    @PostConstruct public void open() {
        try { reader = new Reader(database.toFile()); }
        catch (IOException error) { log.warn("IP-location database unavailable; the activity map will show no locations until configured."); }
    }
    public boolean available() { return reader != null; }

    /** State-level classification for authenticated admin reporting only. Never performs DNS/network lookups. */
    public String region(String ip) {
        if (reader == null) return null; // Unavailable is retryable; do not permanently classify as unknown.
        var address = ClientIpAddress.parse(ip);
        if (!ClientIpAddress.isPublic(address)) return "UNKNOWN";
        try {
            return regionFromRecord(reader.get(address, Map.class));
        } catch (IOException error) { throw new IllegalStateException("IP-region lookup unavailable", error); }
    }
    static String regionFromRecord(Map<?,?> record) {
        if (record == null || !(record.get("country") instanceof Map<?,?> country)
                || !(country.get("iso_code") instanceof String code) || code.isBlank()) return "UNKNOWN";
        if (!"US".equals(code)) return "NON_US";
        if (!(record.get("subdivisions") instanceof java.util.List<?> divisions) || divisions.isEmpty()
                || !(divisions.get(0) instanceof Map<?,?> state)) return "UNKNOWN";
        String iso = state.get("iso_code") instanceof String value ? value : null;
        String name = state.get("names") instanceof Map<?,?> names && names.get("en") instanceof String value ? value : null;
        return CandidateRegions.stateCode(iso, name);
    }

    public Optional<Location> lookup(String ip) {
        var address = ClientIpAddress.parse(ip);
        if (reader == null || !ClientIpAddress.isPublic(address)) return Optional.empty();
        try {
            Map<?, ?> record = reader.get(address, Map.class);
            if (record == null || !(record.get("country") instanceof Map<?, ?> country)
                    || !(record.get("location") instanceof Map<?, ?> location)
                    || !(country.get("iso_code") instanceof String code)
                    || !(location.get("latitude") instanceof Number lat) || !(location.get("longitude") instanceof Number lon)) return Optional.empty();
            double latitude = lat.doubleValue(), longitude = lon.doubleValue();
            if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) return Optional.empty();
            return Optional.of(new Location(code, latitude, longitude));
        } catch (IOException error) { throw new IllegalStateException("IP-location lookup unavailable", error); }
    }
    @PreDestroy public void close() throws IOException { if (reader != null) reader.close(); }
}
