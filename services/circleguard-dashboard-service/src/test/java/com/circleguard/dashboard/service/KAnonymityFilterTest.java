package com.circleguard.dashboard.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class KAnonymityFilterTest {

    private KAnonymityFilter filter;

    @BeforeEach
    void setUp() {
        filter = new KAnonymityFilter();
    }

    @Test
    void apply_withNullStats_returnsEmptyMap() {
        Map<String, Object> result = filter.apply(null);

        assertThat(result).isEmpty();
    }

    @Test
    void apply_withSufficientTotalUsers_doesNotMaskResult() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", 100L);
        stats.put("suspectCount", 10L);

        Map<String, Object> result = filter.apply(stats);

        assertThat(result.get("totalUsers")).isEqualTo(100L);
        assertThat(result.get("suspectCount")).isEqualTo(10L);
    }

    @Test
    void apply_withTotalUsersBelowThreshold_masksEntireResult() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", 3L);
        stats.put("suspectCount", 2L);
        stats.put("department", "Math");

        Map<String, Object> result = filter.apply(stats);

        assertThat(result.get("totalUsers").toString()).startsWith("<");
        assertThat(result).containsKey("note");
    }

    @Test
    void apply_withCountFieldBelowThreshold_masksIndividualCount() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", 50L);
        stats.put("suspectCount", 2L);
        stats.put("confirmedCount", 20L);

        Map<String, Object> result = filter.apply(stats);

        assertThat(result.get("suspectCount").toString()).startsWith("<");
        assertThat(result.get("confirmedCount")).isEqualTo(20L);
    }

    @Test
    void apply_withCustomKThreshold_masksBasedOnCustomValue() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", 50L);
        stats.put("suspectCount", 8L);

        Map<String, Object> resultK5 = filter.apply(stats, 5);
        Map<String, Object> resultK10 = filter.apply(stats, 10);

        // 8 >= 5 → not masked; 8 < 10 → masked
        assertThat(resultK5.get("suspectCount")).isEqualTo(8L);
        assertThat(resultK10.get("suspectCount").toString()).startsWith("<");
    }

    @Test
    void apply_withEmptyStats_returnsEmptyResult() {
        Map<String, Object> result = filter.apply(Map.of());

        assertThat(result).isEmpty();
    }
}
