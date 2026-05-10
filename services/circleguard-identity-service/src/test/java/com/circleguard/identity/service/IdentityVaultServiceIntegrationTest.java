package com.circleguard.identity.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration")
@ActiveProfiles("test")
class IdentityVaultServiceIntegrationTest {

    @Autowired
    private IdentityVaultService vaultService;

    @MockBean
    @SuppressWarnings("rawtypes")
    private KafkaTemplate kafkaTemplate;

    @Test
    void getOrCreateAnonymousId_sameIdentity_returnsSameUuid() {
        String identity = "student.001@circleguard.edu";

        UUID first = vaultService.getOrCreateAnonymousId(identity);
        UUID second = vaultService.getOrCreateAnonymousId(identity);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void getOrCreateAnonymousId_differentIdentities_returnDifferentUuids() {
        UUID idForAlice = vaultService.getOrCreateAnonymousId("alice@circleguard.edu");
        UUID idForBob = vaultService.getOrCreateAnonymousId("bob@circleguard.edu");

        assertThat(idForAlice).isNotEqualTo(idForBob);
    }

    @Test
    void getOrCreateAnonymousId_returnsValidUuid() {
        UUID id = vaultService.getOrCreateAnonymousId("carol@circleguard.edu");

        assertThat(id).isNotNull();
        // Should be parseable as UUID (no exception thrown)
        assertThat(id.toString()).matches("[0-9a-f-]{36}");
    }

    @Test
    void resolveRealIdentity_afterCreate_returnsOriginalIdentity() {
        String originalIdentity = "dave@circleguard.edu";
        UUID anonymousId = vaultService.getOrCreateAnonymousId(originalIdentity);

        String resolved = vaultService.resolveRealIdentity(anonymousId);

        assertThat(resolved).isEqualTo(originalIdentity);
    }
}
