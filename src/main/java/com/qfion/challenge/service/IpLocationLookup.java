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
