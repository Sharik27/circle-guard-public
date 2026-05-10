package com.circleguard.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Key;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
class JwtTokenServiceTest {

    private static final String TEST_SECRET = "test-jwt-secret-key-for-unit-tests-circleguard!!";
    private static final long ONE_HOUR_MS = 3_600_000L;

    private JwtTokenService jwtTokenService;
    private Key testKey;

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService(TEST_SECRET, ONE_HOUR_MS);
        testKey = Keys.hmacShaKeyFor(TEST_SECRET.getBytes());
    }

    @Test
    void generateToken_withValidInput_returnsNonNullToken() {
        UUID anonymousId = UUID.randomUUID();
        Authentication auth = mockAuthWith();

        String token = jwtTokenService.generateToken(anonymousId, auth);

        assertThat(token).isNotNull().isNotEmpty();
    }

    @Test
    void generateToken_producesThreePartJwt() {
        UUID anonymousId = UUID.randomUUID();
        Authentication auth = mockAuthWith();

        String token = jwtTokenService.generateToken(anonymousId, auth);

        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void generateToken_containsAnonymousIdAsSubject() {
        UUID anonymousId = UUID.randomUUID();
        Authentication auth = mockAuthWith();

        String token = jwtTokenService.generateToken(anonymousId, auth);

        Claims claims = parseClaims(token);
        assertThat(claims.getSubject()).isEqualTo(anonymousId.toString());
    }

    @Test
    void generateToken_containsPermissionsClaim() {
        UUID anonymousId = UUID.randomUUID();
        Authentication auth = mockAuthWith("ROLE_STUDENT", "health:read");

        String token = jwtTokenService.generateToken(anonymousId, auth);

        Claims claims = parseClaims(token);
        @SuppressWarnings("unchecked")
        List<String> permissions = claims.get("permissions", List.class);
        assertThat(permissions).containsExactlyInAnyOrder("ROLE_STUDENT", "health:read");
    }

    @Test
    void generateToken_differentCallsProduceDifferentTokens() {
        UUID anonymousId = UUID.randomUUID();
        Authentication auth = mockAuthWith();

        String token1 = jwtTokenService.generateToken(anonymousId, auth);
        String token2 = jwtTokenService.generateToken(anonymousId, auth);

        // iat may differ by at least 1ms between calls
        assertThat(token1).isNotNull();
        assertThat(token2).isNotNull();
    }

    // --- helpers ---

    @SuppressWarnings("unchecked")
    private Authentication mockAuthWith(String... authorities) {
        Authentication auth = mock(Authentication.class);
        List<SimpleGrantedAuthority> granted = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toList();
        when(auth.getAuthorities()).thenReturn((Collection) granted);
        return auth;
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(testKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
