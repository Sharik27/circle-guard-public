package com.circleguard.auth.service;

import com.circleguard.auth.model.LocalUser;
import com.circleguard.auth.model.Permission;
import com.circleguard.auth.model.Role;
import com.circleguard.auth.repository.LocalUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private LocalUserRepository userRepository;

    private CustomUserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new CustomUserDetailsService(userRepository);
    }

    @Test
    void loadUserByUsername_existingActiveUser_returnsUserDetails() {
        LocalUser user = activeUserWithRole("STUDENT");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        UserDetails result = service.loadUserByUsername("testuser");

        assertThat(result.getUsername()).isEqualTo("testuser");
        assertThat(result.isEnabled()).isTrue();
    }

    @Test
    void loadUserByUsername_activeUserWithRole_hasRolePrefixedAuthority() {
        LocalUser user = activeUserWithRole("HEALTH_CENTER");
        when(userRepository.findByUsername("nurse")).thenReturn(Optional.of(user));

        UserDetails result = service.loadUserByUsername("nurse");

        assertThat(result.getAuthorities())
                .extracting("authority")
                .contains("ROLE_HEALTH_CENTER");
    }

    @Test
    void loadUserByUsername_activeUserWithPermission_hasGranularAuthority() {
        Permission perm = Permission.builder().id(UUID.randomUUID()).name("health:read").build();
        Role role = Role.builder().id(UUID.randomUUID()).name("STUDENT")
                .permissions(Set.of(perm)).build();
        LocalUser user = LocalUser.builder()
                .id(UUID.randomUUID()).username("student1").password("hashed")
                .isActive(true).roles(Set.of(role)).build();
        when(userRepository.findByUsername("student1")).thenReturn(Optional.of(user));

        UserDetails result = service.loadUserByUsername("student1");

        assertThat(result.getAuthorities())
                .extracting("authority")
                .contains("health:read");
    }

    @Test
    void loadUserByUsername_unknownUser_throwsUsernameNotFoundException() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void loadUserByUsername_inactiveUser_throwsDisabledException() {
        LocalUser inactive = LocalUser.builder()
                .id(UUID.randomUUID()).username("inactive").password("hashed")
                .isActive(false).roles(Set.of()).build();
        when(userRepository.findByUsername("inactive")).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.loadUserByUsername("inactive"))
                .isInstanceOf(DisabledException.class);
    }

    // --- helpers ---

    private LocalUser activeUserWithRole(String roleName) {
        Role role = Role.builder()
                .id(UUID.randomUUID()).name(roleName).permissions(Set.of()).build();
        return LocalUser.builder()
                .id(UUID.randomUUID()).username("testuser").password("hashed")
                .isActive(true).roles(Set.of(role)).build();
    }
}
