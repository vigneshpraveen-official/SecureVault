package com.securevault.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.securevault.common.audit.AuditLogRepository;
import com.securevault.vault.CredentialRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * P7.1 (M-31/M-32) rollback proof, now automated: {@code AuditServiceImpl}'s test-only {@code
 * app.testing.force-audit-failure} flag throws AFTER the credential save inside {@code
 * CredentialServiceImpl.create}'s single {@code @Transactional} boundary but BEFORE the audit row
 * is written — proving the whole transaction, including the already-executed credential INSERT,
 * rolls back together rather than leaving a credential with no audit trail.
 *
 * <p>A separate {@code @TestPropertySource} means this class gets its own Spring context (distinct
 * from the other P7.2 classes), but it still reuses the same running Postgres/Redis containers
 * declared on {@link AbstractIntegrationTest} (container reuse is independent of Spring context
 * caching).
 */
@TestPropertySource(properties = "app.testing.force-audit-failure=true")
class AuditRollbackIntegrationTest extends AbstractIntegrationTest {

    @Autowired private CredentialRepository credentialRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    @Test
    void should_rollBackTheCredentialInsert_when_theAuditWriteInTheSameTransactionFails()
            throws Exception {
        String accessToken = registerAndLogin("rollback@example.com", "Str0ng!Pass1");
        long credentialsBefore = credentialRepository.count();
        long auditRowsBefore = auditLogRepository.count();

        String createBody =
                objectMapper.writeValueAsString(
                        Map.of(
                                "title", "Should Not Survive",
                                "username", "dave",
                                "password", "WontPersist1!"));
        mockMvc.perform(
                        post("/api/vault")
                                .header("Authorization", "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createBody))
                .andExpect(status().isInternalServerError());

        assertEquals(
                credentialsBefore,
                credentialRepository.count(),
                "the credential INSERT must have rolled back with the failed audit write");
        assertEquals(
                auditRowsBefore,
                auditLogRepository.count(),
                "no audit row can exist for a create that never committed");
        assertTrue(
                credentialRepository.findAll().stream()
                        .noneMatch(c -> "Should Not Survive".equals(c.getTitle())),
                "no partial row with this title should exist anywhere, active or not");
    }
}
