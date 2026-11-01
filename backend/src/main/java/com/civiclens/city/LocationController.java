package com.civiclens.city;

import com.civiclens.city.NominatimLocationService.LocationSuggestion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@Tag(name = "Global locations")
public class LocationController {

    private final NominatimLocationService locations;

    public LocationController(NominatimLocationService locations) { this.locations = locations; }

    @Operation(summary = "Search worldwide settlements. Call only after the user explicitly submits a search; this is not autocomplete.")
    @GetMapping("/api/v1/locations/search")
    public List<LocationSuggestion> search(
            @RequestParam @NotBlank @Size(min = 3, max = 200) String query) {
        return locations.search(query);
    }

    @Operation(summary = "Reverse-geocode a user-selected GPS coordinate")
    @GetMapping("/api/v1/locations/reverse")
    public LocationSuggestion reverse(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double latitude,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double longitude) {
        return locations.reverse(latitude, longitude);
    }
}
