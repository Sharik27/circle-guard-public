package com.circleguard.notification.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class NotificationKafkaIntegrationTest {

    @Autowired
    private ExposureNotificationListener listener;

    @MockBean
    private NotificationDispatcher dispatcher;

    @MockBean
    private LmsService lmsService;

    @MockBean
    private EmailService emailService;

    @MockBean
    private SmsService smsService;

    @MockBean
    private PushService pushService;

    @MockBean
    private JavaMailSender mailSender;

    @MockBean
    @SuppressWarnings("rawtypes")
    private KafkaTemplate kafkaTemplate;

    @MockBean
    private WebClient.Builder webClientBuilder;

    @Test
    void handleStatusChange_withSuspectStatus_callsDispatcher() {
        when(lmsService.syncRemoteAttendance(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        String event = "{\"anonymousId\":\"user-integration-001\",\"status\":\"SUSPECT\"}";
        listener.handleStatusChange(event);

        verify(dispatcher).dispatch("user-integration-001", "SUSPECT");
        verify(lmsService).syncRemoteAttendance("user-integration-001", "SUSPECT");
    }

    @Test
    void handleStatusChange_withActiveStatus_skipsDispatch() {
        String event = "{\"anonymousId\":\"user-integration-002\",\"status\":\"ACTIVE\"}";
        listener.handleStatusChange(event);

        verify(dispatcher, never()).dispatch(anyString(), anyString());
    }

    @Test
    void handleStatusChange_withConfirmedStatus_callsDispatcher() {
        when(lmsService.syncRemoteAttendance(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        String event = "{\"anonymousId\":\"user-integration-003\",\"status\":\"CONFIRMED\"}";
        listener.handleStatusChange(event);

        verify(dispatcher).dispatch("user-integration-003", "CONFIRMED");
    }

    @Test
    void handleStatusChange_withMalformedJson_doesNotThrowException() {
        // Listener should log the error and return gracefully
        String badJson = "{ invalid json }";

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> listener.handleStatusChange(badJson)
        );

        verify(dispatcher, never()).dispatch(anyString(), anyString());
    }
}
