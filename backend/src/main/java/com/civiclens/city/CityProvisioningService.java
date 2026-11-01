package com.civiclens.city;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Resolves geocoder-verified settlements into stable CivicLens location records.
 * Departments created here are internal triage queues, not municipal integrations.
 */
@Service
public class CityProvisioningService {

    private record DepartmentSpec(String code, String name, String description, List<String> categories) { }

    private static final List<DepartmentSpec> DEFAULT_QUEUES = List.of(
            new DepartmentSpec("ROADS", "Roads & Footpaths", "Internal triage queue for roads, potholes and footpaths.",
                    List.of("ROAD_DAMAGE")),
            new DepartmentSpec("ELECTRICAL", "Electrical & Streetlights", "Internal triage queue for streetlights, signals and electrical hazards.",
                    List.of("STREETLIGHT", "TRAFFIC_SIGNAL", "ELECTRICAL_HAZARD")),
            new DepartmentSpec("SANITATION", "Sanitation & Waste", "Internal triage queue for garbage and waste collection.",
                    List.of("GARBAGE")),
            new DepartmentSpec("WATER", "Water Supply", "Internal triage queue for water leaks and supply issues.",
                    List.of("WATER_LEAKAGE")),
            new DepartmentSpec("DRAINAGE", "Drainage & Sewerage", "Internal triage queue for drainage, flooding and sewage.",
                    List.of("DRAINAGE", "FLOODING", "SEWAGE")),
            new DepartmentSpec("PARKS", "Parks & Public Property", "Internal triage queue for trees and public property.",
                    List.of("TREE_FALL", "PUBLIC_PROPERTY_DAMAGE"))
    );

    private final JdbcTemplate jdbc;
    private final CityRepository cities;

    public CityProvisioningService(JdbcTemplate jdbc, CityRepository cities) {
        this.jdbc = jdbc;
        this.cities = cities;
    }

    @Transactional
    public City resolve(String cityName, String state, String country, String countryCode, Double latitude, Double longitude) {
        String cleanName = cityName.strip();
        String cleanState = state == null || state.isBlank() ? null : state.strip();
        String cleanCountry = country.strip();
        String cleanCountryCode = countryCode.strip().toUpperCase(Locale.ROOT);
        String locationKey = cleanCountryCode + ":" + keyPart(cleanState) + ":" + keyPart(cleanName);

        UUID proposedId = UUID.randomUUID();
        String code = "LOC_" + proposedId.toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        UUID cityId = jdbc.queryForObject("""
                INSERT INTO cities (id, code, name, state, country, country_code, location_key, center_latitude, center_longitude, active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE)
                ON CONFLICT (location_key) DO UPDATE SET
                    active = TRUE,
                    center_latitude = COALESCE(cities.center_latitude, EXCLUDED.center_latitude),
                    center_longitude = COALESCE(cities.center_longitude, EXCLUDED.center_longitude)
                RETURNING id
                """, UUID.class, proposedId, code, cleanName, cleanState, cleanCountry, cleanCountryCode, locationKey, latitude, longitude);

        ensureDefaultQueues(cityId);
        return cities.findById(cityId).orElseThrow(() -> new IllegalStateException("Resolved city was not persisted."));
    }

    private void ensureDefaultQueues(UUID cityId) {
        for (DepartmentSpec spec : DEFAULT_QUEUES) {
            UUID departmentId = jdbc.queryForObject("""
                    INSERT INTO departments (id, city_id, code, name, description, active)
                    VALUES (?, ?, ?, ?, ?, TRUE)
                    ON CONFLICT (city_id, code) DO UPDATE SET active = TRUE
                    RETURNING id
                    """, UUID.class, UUID.randomUUID(), cityId, spec.code(), spec.name(), spec.description());

            for (String category : spec.categories()) {
                jdbc.update("""
                        INSERT INTO department_categories (city_id, category, department_id)
                        VALUES (?, ?, ?)
                        ON CONFLICT (city_id, category) DO UPDATE SET department_id = EXCLUDED.department_id
                        """, cityId, category, departmentId);
            }
        }
    }

    private static String keyPart(String value) {
        if (value == null || value.isBlank()) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .strip()
                .replaceAll("\\s+", " ");
    }
}
