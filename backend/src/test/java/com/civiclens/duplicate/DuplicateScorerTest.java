package com.civiclens.duplicate;

import com.civiclens.duplicate.DuplicateScorer.Existing;
import com.civiclens.duplicate.DuplicateScorer.Probe;
import com.civiclens.incident.IncidentCategory;
import com.civiclens.incident.IncidentStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DuplicateScorerTest {

    final DuplicateScorer scorer = new DuplicateScorer(300);
    static final String POTHOLE = TextSimilarity.normalize("Large pothole near the main market, bikes are swerving to avoid it");

    Existing existing(String text, String locality, Double lat, Double lng, IncidentCategory cat, IncidentStatus status) {
        return new Existing(TextSimilarity.normalize(text), locality, lat, lng, cat, status);
    }

    @Test
    void sameDescriptionSameLocationIsStrong() {
        var r = scorer.score(new Probe(POTHOLE, "Shivaji Nagar", 18.5308, 73.8475, IncidentCategory.ROAD_DAMAGE),
                existing("Large pothole near the main market, bikes are swerving to avoid it", "Shivaji Nagar",
                        18.5308, 73.8475, IncidentCategory.ROAD_DAMAGE, IncidentStatus.SUBMITTED));
        assertThat(r.score()).isGreaterThan(0.9);
        assertThat(r.reasons()).extracting(DuplicateScorer.Reason::code)
                .contains("SIMILAR_DESCRIPTION", "NEARBY", "SAME_LOCALITY", "SAME_CATEGORY");
    }

    @Test
    void similarWordingSameLocationIsStillAMatch() {
        var r = scorer.score(new Probe(TextSimilarity.normalize("Big potholes close to market, two-wheelers swerve"),
                        null, 18.5309, 73.8476, IncidentCategory.ROAD_DAMAGE),
                existing("Large pothole near the main market, bikes are swerving to avoid it", null,
                        18.5308, 73.8475, IncidentCategory.ROAD_DAMAGE, IncidentStatus.SUBMITTED));
        assertThat(r).isNotNull();
        assertThat(r.score()).isGreaterThan(0.55);
    }

    @Test
    void differentDescriptionsSameCoordinatesDifferentCategoryAreExcluded() {
        var r = scorer.score(new Probe(TextSimilarity.normalize("Streetlight not working, lane completely dark"), null,
                        18.5308, 73.8475, IncidentCategory.STREETLIGHT),
                existing("Large pothole near the main market", null, 18.5308, 73.8475,
                        IncidentCategory.ROAD_DAMAGE, IncidentStatus.SUBMITTED));
        assertThat(r).isNull();
    }

    @Test
    void differentDescriptionsSameCoordinatesSameCategoryIsWeakCandidate() {
        var r = scorer.score(new Probe(TextSimilarity.normalize("Road surface broken badly here"), null,
                        18.5308, 73.8475, IncidentCategory.ROAD_DAMAGE),
                existing("Large pothole near the main market", null, 18.5308, 73.8475,
                        IncidentCategory.ROAD_DAMAGE, IncidentStatus.SUBMITTED));
        assertThat(r).isNotNull();
        assertThat(r.score()).isLessThan(0.65);
    }

    @Test
    void farApartIsExcludedEvenWithIdenticalText() {
        var r = scorer.score(new Probe(POTHOLE, null, 18.5308, 73.8475, IncidentCategory.ROAD_DAMAGE),
                existing("Large pothole near the main market, bikes are swerving to avoid it", null, 18.60, 73.95,
                        IncidentCategory.ROAD_DAMAGE, IncidentStatus.SUBMITTED));
        assertThat(r).isNull();
    }

    @Test
    void missingCoordinatesFallBackToLocality() {
        var r = scorer.score(new Probe(POTHOLE, "shivaji-nagar", null, null, IncidentCategory.ROAD_DAMAGE),
                existing("Large pothole near the main market, bikes are swerving to avoid it", "Shivaji Nagar",
                        18.5308, 73.8475, IncidentCategory.ROAD_DAMAGE, IncidentStatus.SUBMITTED));
        assertThat(r.distanceMeters()).isNull();
        assertThat(r.reasons()).extracting(DuplicateScorer.Reason::code).contains("SAME_LOCALITY");
        assertThat(r.score()).isGreaterThan(0.8);
    }

    @Test
    void missingLocalityAndCoordinatesReliesOnTextAndCategory() {
        var r = scorer.score(new Probe(POTHOLE, null, null, null, null),
                existing("Large pothole near the main market, bikes are swerving to avoid it", null, null, null,
                        IncidentCategory.ROAD_DAMAGE, IncidentStatus.SUBMITTED));
        assertThat(r.score()).isCloseTo(0.45, within(0.01));
    }

    @Test
    void relatedCategoryCountsHalf() {
        var r = scorer.score(new Probe(TextSimilarity.normalize("Drain overflowing water on road"), "Aundh", null, null,
                        IncidentCategory.DRAINAGE),
                existing("Road flooded because drain overflowing", "Aundh", null, null, IncidentCategory.FLOODING,
                        IncidentStatus.SUBMITTED));
        assertThat(r.reasons()).extracting(DuplicateScorer.Reason::code).contains("RELATED_CATEGORY");
    }

    @Test
    void resolvedCandidateIsDiscountedAndExplained() {
        Probe p = new Probe(POTHOLE, null, 18.5308, 73.8475, IncidentCategory.ROAD_DAMAGE);
        var open = scorer.score(p, existing("Large pothole near the main market, bikes are swerving to avoid it", null,
                18.5308, 73.8475, IncidentCategory.ROAD_DAMAGE, IncidentStatus.SUBMITTED));
        var resolved = scorer.score(p, existing("Large pothole near the main market, bikes are swerving to avoid it", null,
                18.5308, 73.8475, IncidentCategory.ROAD_DAMAGE, IncidentStatus.RESOLVED));
        assertThat(resolved.score()).isLessThan(open.score());
        assertThat(resolved.reasons()).extracting(DuplicateScorer.Reason::code).contains("PREVIOUSLY_RESOLVED");
    }

    @Test
    void veryShortDescriptionsAreWeakEvidence() {
        var r = scorer.score(new Probe(TextSimilarity.normalize("pothole"), null, null, null, null),
                existing("pothole", null, null, null, IncidentCategory.ROAD_DAMAGE, IncidentStatus.SUBMITTED));
        assertThat(r.score()).isLessThan(0.35);
    }

    @Test
    void textNormalizationAndSimilarity() {
        assertThat(TextSimilarity.normalize("The STREET-lights aren't working!!")).isEqualTo("street light not work");
        assertThat(TextSimilarity.similarity("a b c", "a b c")).isEqualTo(1.0);
        assertThat(TextSimilarity.similarity("", "anything")).isEqualTo(0.0);
        assertThat(TextSimilarity.similarity(TextSimilarity.normalize("garbage pile not collected"),
                TextSimilarity.normalize("streetlight broken pole"))).isLessThan(0.2);
    }

    @Test
    void negationIsPreservedDuringNormalizationAndSimilarity() {
        String working = TextSimilarity.normalize("Streetlight is working");
        String notWorking = TextSimilarity.normalize("Streetlight is not working");
        String contractedNotWorking = TextSimilarity.normalize("Streetlight isn't working");

        assertThat(working).isNotEqualTo(notWorking);
        assertThat(notWorking).isEqualTo(contractedNotWorking);
        assertThat(notWorking).isEqualTo("streetlight not work");
        assertThat(TextSimilarity.similarity(working, notWorking)).isLessThan(1.0);
        assertThat(TextSimilarity.normalize("No streetlight is working"))
                .isEqualTo("no streetlight work");
    }

    @Test
    void haversineDistance() {
        // ~111 km per degree of latitude
        assertThat(GeoDistance.meters(0, 0, 1, 0)).isCloseTo(111_195, within(100.0));
        assertThat(GeoDistance.meters(18.5308, 73.8475, 18.5308, 73.8475)).isZero();
        assertThat(GeoDistance.meters(18.5308, 73.8475, 18.5317, 73.8475)).isCloseTo(100, within(2.0));
    }
}
