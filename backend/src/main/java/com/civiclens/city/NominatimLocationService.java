package com.civiclens.city;

import com.civiclens.common.ApiException;
import com.civiclens.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Server-side OpenStreetMap Nominatim adapter. Search is explicitly user-triggered (never type-ahead),
 * responses are cached, and a single JVM-wide throttle keeps this application under the public service's
 * one-request-per-second ceiling. Replace the provider through configuration when traffic grows.
 */
@Service
public class NominatimLocationService {

    public record LocationSuggestion(String label, double lat, double lon, String cityName, String state,
                                      String country, String countryCode, String locality) { }

    private record CacheEntry<T>(T value, Instant expiresAt) { }

    private static final Duration CACHE_TTL = Duration.ofMinutes(15);
    private static final long MIN_REQUEST_INTERVAL_MS = 1_100L;

    private final RestClient client;
    private final boolean enabled;
    private final Object upstreamLock = new Object();
    private final Map<String, CacheEntry<List<LocationSuggestion>>> searchCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<LocationSuggestion>> reverseCache = new ConcurrentHashMap<>();
    private long lastRequestStartedAt;

    public NominatimLocationService(
            @Value("${civiclens.geocoding.base-url:https://nominatim.openstreetmap.org}") String baseUrl,
            @Value("${civiclens.geocoding.user-agent:CivicLensAI/1.0 (+https://github.com/Shubh12970/CivicLens-AI)}") String userAgent,
            @Value("${civiclens.geocoding.enabled:true}") boolean enabled) {
        this.enabled = enabled;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(7));
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("User-Agent", userAgent)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public List<LocationSuggestion> search(String query) {
        ensureEnabled();
        String clean = query == null ? "" : query.strip();
        if (clean.length() < 3 || clean.length() > 200) {
            throw ApiException.validation("query", "Location search must contain 3-200 characters.");
        }
        String key = clean.toLowerCase(Locale.ROOT);
        List<LocationSuggestion> cached = cached(searchCache, key);
        if (cached != null) return cached;

        JsonNode response = limited(() -> client.get()
                .uri(uri -> uri.path("/search").queryParam("q", clean).queryParam("format", "jsonv2")
                        .queryParam("addressdetails", "1").queryParam("limit", "6").build())
                .retrieve().body(JsonNode.class));
        List<LocationSuggestion> results = new ArrayList<>();
        if (response != null && response.isArray()) {
            response.forEach(node -> toSuggestion(node).ifPresent(results::add));
        }
        putCache(searchCache, key, List.copyOf(results));
        return List.copyOf(results);
    }

    public LocationSuggestion reverse(double latitude, double longitude) {
        ensureEnabled();
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw ApiException.validation("coordinates", "Coordinates are outside valid latitude/longitude ranges.");
        }
        String key = String.format(Locale.ROOT, "%.5f:%.5f", latitude, longitude);
        LocationSuggestion cached = cached(reverseCache, key);
        if (cached != null) return cached;

        JsonNode response = limited(() -> client.get()
                .uri(uri -> uri.path("/reverse").queryParam("lat", latitude).queryParam("lon", longitude)
                        .queryParam("format", "jsonv2").queryParam("addressdetails", "1").build())
                .retrieve().body(JsonNode.class));
        LocationSuggestion suggestion = response == null ? null : toSuggestion(response).orElse(null);
        if (suggestion == null) throw ApiException.notFound("Location at the selected coordinates");
        putCache(reverseCache, key, suggestion);
        return suggestion;
    }

    private void ensureEnabled() {
        if (!enabled) throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Location search is temporarily unavailable.");
    }

    private <T> T limited(Supplier<T> request) {
        synchronized (upstreamLock) {
            long wait = MIN_REQUEST_INTERVAL_MS - (System.currentTimeMillis() - lastRequestStartedAt);
            if (lastRequestStartedAt > 0 && wait > 0) {
                try { Thread.sleep(wait); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Location lookup was interrupted. Please try again."); }
            }
            lastRequestStartedAt = System.currentTimeMillis();
            try {
                return request.get();
            } catch (RestClientException ex) {
                throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "The location provider is temporarily unavailable. Please try again.");
            }
        }
    }

    private static java.util.Optional<LocationSuggestion> toSuggestion(JsonNode node) {
        JsonNode address = node.path("address");
        String city = first(address, "city", "town", "village", "municipality", "hamlet", "locality");
        String country = text(address, "country");
        String countryCode = text(address, "country_code");
        String label = text(node, "display_name");
        if (city == null || country == null || countryCode == null || label == null) return java.util.Optional.empty();
        try {
            double lat = Double.parseDouble(node.path("lat").asText());
            double lon = Double.parseDouble(node.path("lon").asText());
            String state = first(address, "state", "province", "region", "state_district");
            String locality = first(address, "neighbourhood", "suburb", "quarter", "city_district", "hamlet", "village");
            if (locality == null) locality = city;
            return java.util.Optional.of(new LocationSuggestion(label, lat, lon, city, state, country,
                    countryCode.toUpperCase(Locale.ROOT), locality));
        } catch (NumberFormatException ex) {
            return java.util.Optional.empty();
        }
    }

    private static String first(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null) return value;
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static <T> T cached(Map<String, CacheEntry<T>> cache, String key) {
        CacheEntry<T> entry = cache.get(key);
        if (entry == null) return null;
        if (entry.expiresAt().isBefore(Instant.now())) { cache.remove(key, entry); return null; }
        return entry.value();
    }

    private static <T> void putCache(Map<String, CacheEntry<T>> cache, String key, T value) {
        if (cache.size() > 2_000) cache.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(Instant.now()));
        cache.put(key, new CacheEntry<>(value, Instant.now().plus(CACHE_TTL)));
    }
}
