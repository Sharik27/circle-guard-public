package com.circleguard.dashboard.controller;

import com.circleguard.dashboard.service.AnalyticsService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("integration")
@WebMvcTest(AnalyticsController.class)
class DashboardControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsService analyticsService;

    @Test
    void getSummary_returns200WithSummaryData() throws Exception {
        Map<String, Object> summary = Map.of("activeUsers", 500, "campus", "main");
        when(analyticsService.getCampusSummary()).thenReturn(summary);

        mockMvc.perform(get("/api/v1/analytics/summary")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeUsers").value(500));
    }

    @Test
    void getDepartmentStats_withValidDepartment_returns200() throws Exception {
        Map<String, Object> stats = Map.of("department", "Engineering", "totalUsers", 120L);
        when(analyticsService.getDepartmentStats("Engineering")).thenReturn(stats);

        mockMvc.perform(get("/api/v1/analytics/department/Engineering")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.department").value("Engineering"));
    }

    @Test
    void getTimeSeries_withDefaultParams_returns200() throws Exception {
        List<Map<String, Object>> series = List.of(
                Map.of("status", "ACTIVE", "total", 200),
                Map.of("status", "SUSPECT", "total", 15)
        );
        when(analyticsService.getTimeSeries(anyString(), anyInt())).thenReturn(series);

        mockMvc.perform(get("/api/v1/analytics/time-series")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void getTimeSeries_withDailyPeriod_passesParamToService() throws Exception {
        List<Map<String, Object>> series = List.of(Map.of("status", "ACTIVE", "total", 500));
        when(analyticsService.getTimeSeries("daily", 7)).thenReturn(series);

        mockMvc.perform(get("/api/v1/analytics/time-series")
                        .param("period", "daily")
                        .param("limit", "7")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void getHealthBoard_returns200() throws Exception {
        Map<String, Object> stats = Map.of("totalActive", 400, "totalSuspect", 12);
        when(analyticsService.getGlobalHealthStats()).thenReturn(stats);

        mockMvc.perform(get("/api/v1/analytics/health-board")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalActive").value(400));
    }
}
