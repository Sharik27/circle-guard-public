package com.circleguard.auth.controller;

import com.circleguard.auth.client.IdentityClient;
import com.circleguard.auth.security.SecurityConfig;
import com.circleguard.auth.service.CustomUserDetailsService;
import com.circleguard.auth.service.JwtTokenService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("integration")
@WebMvcTest(LoginController.class)
@Import(SecurityConfig.class)
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthenticationManager authManager;

    @MockBean
    private JwtTokenService jwtService;

    @MockBean
    private IdentityClient identityClient;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void login_withInvalidCredentials_returns401() throws Exception {
        when(authManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"unknown\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void visitorHandoff_withValidAnonymousId_returnsTokenAndHandoffPayload() throws Exception {
        UUID anonymousId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(jwtService.generateToken(any(UUID.class), any())).thenReturn("visitor-jwt-token");

        mockMvc.perform(post("/api/v1/auth/visitor/handoff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anonymousId\":\"" + anonymousId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("visitor-jwt-token"))
                .andExpect(jsonPath("$.handoffPayload").exists());
    }

    @Test
    void visitorHandoff_withMissingAnonymousId_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/visitor/handoff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_withValidCredentials_returnsJwtAndAnonymousId() throws Exception {
        UUID anonymousId = UUID.randomUUID();
        var auth = new UsernamePasswordAuthenticationToken("alice", null, List.of());
        when(authManager.authenticate(any())).thenReturn(auth);
        when(identityClient.getAnonymousId("alice")).thenReturn(anonymousId);
        when(jwtService.generateToken(any(UUID.class), any())).thenReturn("jwt-token-123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token-123"))
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.anonymousId").value(anonymousId.toString()));
    }
}
