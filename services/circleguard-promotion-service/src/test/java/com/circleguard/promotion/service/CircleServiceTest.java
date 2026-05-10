package com.circleguard.promotion.service;

import com.circleguard.promotion.model.graph.CircleNode;
import com.circleguard.promotion.model.graph.UserNode;
import com.circleguard.promotion.repository.graph.CircleNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class CircleServiceTest {

    @Mock
    private CircleNodeRepository circleRepository;

    @Mock
    private HealthStatusService healthStatusService;

    private CircleService circleService;

    @BeforeEach
    void setUp() {
        circleService = new CircleService(circleRepository, healthStatusService);
    }

    @Test
    void createCircle_generatesInviteCodeWithMeshPrefix() {
        when(circleRepository.existsByInviteCode(anyString())).thenReturn(false);
        when(circleRepository.save(any(CircleNode.class))).thenAnswer(inv -> inv.getArgument(0));

        CircleNode result = circleService.createCircle("Study Group", "room-101");

        assertThat(result.getInviteCode()).startsWith("MESH-");
        assertThat(result.getInviteCode()).hasSize(9); // "MESH-" (5) + 4 chars
    }

    @Test
    void createCircle_persistsCorrectNameAndLocation() {
        when(circleRepository.existsByInviteCode(anyString())).thenReturn(false);
        when(circleRepository.save(any(CircleNode.class))).thenAnswer(inv -> inv.getArgument(0));

        CircleNode result = circleService.createCircle("Library Circle", "lib-floor-2");

        assertThat(result.getName()).isEqualTo("Library Circle");
        assertThat(result.getLocationId()).isEqualTo("lib-floor-2");
        assertThat(result.getIsActive()).isTrue();
    }

    @Test
    void joinCircle_withInvalidCode_throwsRuntimeException() {
        when(circleRepository.joinCircle(anyString(), anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> circleService.joinCircle("user-abc", "MESH-XXXX"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void forceFenceCircle_promotesActiveMembers() {
        UserNode activeUser = UserNode.builder()
                .anonymousId("user-001").status("ACTIVE").build();
        UserNode suspectUser = UserNode.builder()
                .anonymousId("user-002").status("SUSPECT").build();
        CircleNode circle = CircleNode.builder()
                .id(1L).name("Test Circle")
                .members(Set.of(activeUser, suspectUser)).build();

        when(circleRepository.findById(1L)).thenReturn(Optional.of(circle));
        when(circleRepository.save(any(CircleNode.class))).thenReturn(circle);

        circleService.forceFenceCircle(1L);

        verify(healthStatusService).updateStatus("user-001", "PROBABLE");
    }

    @Test
    void getUserCircles_withUnknownUser_returnsEmptyList() {
        when(circleRepository.findCirclesByUser("unknown")).thenReturn(java.util.List.of());

        assertThat(circleService.getUserCircles("unknown")).isEmpty();
    }
}
