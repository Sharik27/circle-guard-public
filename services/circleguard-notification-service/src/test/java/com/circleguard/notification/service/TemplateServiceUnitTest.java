package com.circleguard.notification.service;

import freemarker.template.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@Tag("unit")
class TemplateServiceUnitTest {

    private TemplateService templateService;

    @BeforeEach
    void setUp() {
        templateService = new TemplateService(mock(Configuration.class));
        ReflectionTestUtils.setField(templateService, "testingUrl", "https://test.circleguard.com/testing");
        ReflectionTestUtils.setField(templateService, "isolationUrl", "https://test.circleguard.com/isolation");
        ReflectionTestUtils.setField(templateService, "guidelinesDeepLink", "circleguard://guidelines");
    }

    @Test
    void generatePushContent_forSuspectStatus_containsAlertText() {
        String content = templateService.generatePushContent("SUSPECT");

        assertThat(content).containsIgnoringCase("SUSPECT");
        assertThat(content).isNotEmpty();
    }

    @Test
    void generatePushContent_forProbableStatus_containsMonitorText() {
        String content = templateService.generatePushContent("PROBABLE");

        assertThat(content).containsIgnoringCase("PROBABLE");
        assertThat(content).containsIgnoringCase("monitor");
    }

    @Test
    void generatePushContent_forUnknownStatus_returnsDefaultMessage() {
        String content = templateService.generatePushContent("RECOVERED");

        assertThat(content).contains("RECOVERED");
    }

    @Test
    void generateSmsContent_includesStatusInMessage() {
        String content = templateService.generateSmsContent("CONFIRMED");

        assertThat(content).contains("CONFIRMED");
        assertThat(content).isNotEmpty();
    }

    @Test
    void generatePushMetadata_forSuspectStatus_returnsDeepLink() {
        Map<String, String> metadata = templateService.generatePushMetadata("SUSPECT");

        assertThat(metadata).containsKey("url");
        assertThat(metadata.get("url")).isEqualTo("circleguard://guidelines");
    }

    @Test
    void generatePushMetadata_forProbableStatus_returnsDeepLink() {
        Map<String, String> metadata = templateService.generatePushMetadata("PROBABLE");

        assertThat(metadata).containsKey("url");
    }

    @Test
    void generatePushMetadata_forOtherStatus_returnsEmptyMap() {
        Map<String, String> metadata = templateService.generatePushMetadata("ACTIVE");

        assertThat(metadata).isEmpty();
    }

    @Test
    void generateEmailContent_whenFreeMarkerFails_returnsFallbackMessage() {
        // FreeMarker config is mocked, so template loading will fail → fallback triggers
        String content = templateService.generateEmailContent("SUSPECT", "testuser");

        assertThat(content).isNotNull().isNotEmpty();
        assertThat(content).contains("SUSPECT");
    }
}
