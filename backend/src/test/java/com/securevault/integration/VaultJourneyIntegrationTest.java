package com.securevault.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * P7.2 journey 1: register -> login -> create credential -> read (decrypted) -> update -> soft
 * delete -> trash -> restore -> permanent delete, driven entirely through the real HTTP layer
 * (MockMvc + the actual JWT filter chain) against a real Postgres container — the same journey
 * documented live with curl throughout Phase 1-4, now pinned as an automated test.
 */
class VaultJourneyIntegrationTest extends AbstractIntegrationTest {

    @Test
    void should_completeTheFullCredentialLifecycle_fromCreateToPermanentDelete() throws Exception {
        String accessToken = registerAndLogin("vault-journey@example.com", "Str0ng!Pass1");

        String createBody =
                objectMapper.writeValueAsString(
                        Map.of(
                                "title", "GitHub",
                                "username", "dave",
                                "password", "GhSecret1!",
                                "category", "DEVELOPMENT"));
        ResultActions createResult =
                mockMvc.perform(
                                post("/api/vault")
                                        .header("Authorization", "Bearer " + accessToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(createBody))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.data.title").value("GitHub"))
                        .andExpect(jsonPath("$.data.strengthScore").exists());
        String createJson = createResult.andReturn().getResponse().getContentAsString();
        long credentialId = objectMapper.readTree(createJson).path("data").path("id").asLong();

        // List view must never carry the password field at all.
        mockMvc.perform(get("/api/vault").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].password").doesNotExist());

        // Single-credential reveal decrypts correctly.
        mockMvc.perform(
                        get("/api/vault/" + credentialId)
                                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.password").value("GhSecret1!"));

        // Update the password — the new value must decrypt correctly afterwards.
        String updateBody = objectMapper.writeValueAsString(Map.of("password", "NewGhSecret2@"));
        mockMvc.perform(
                        put("/api/vault/" + credentialId)
                                .header("Authorization", "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updateBody))
                .andExpect(status().isOk());
        mockMvc.perform(
                        get("/api/vault/" + credentialId)
                                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.password").value("NewGhSecret2@"));

        // Reusing the immediately-previous password must be rejected (P4.2 reuse window).
        String reuseBody = objectMapper.writeValueAsString(Map.of("password", "GhSecret1!"));
        mockMvc.perform(
                        put("/api/vault/" + credentialId)
                                .header("Authorization", "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(reuseBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PASSWORD_REUSED"));

        // Soft delete: gone from the active list, present in the trash.
        mockMvc.perform(
                        delete("/api/vault/" + credentialId)
                                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(
                        get("/api/vault/" + credentialId)
                                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/vault/trash").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(credentialId));

        // Restore: active again, gone from the trash.
        mockMvc.perform(
                        put("/api/vault/" + credentialId + "/restore")
                                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
        mockMvc.perform(
                        get("/api/vault/" + credentialId)
                                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // Permanent delete: gone for good, including from the trash.
        mockMvc.perform(
                        delete("/api/vault/" + credentialId)
                                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(
                        delete("/api/vault/" + credentialId + "/permanent")
                                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/vault/trash").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        // A second permanent-delete attempt on the now-gone credential is a 404, not a 500.
        mockMvc.perform(
                        delete("/api/vault/" + credentialId + "/permanent")
                                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_denyAccessToAnotherUsersCredential_acrossTheWholeApi() throws Exception {
        String ownerToken = registerAndLogin("owner-isolation@example.com", "Str0ng!Pass1");
        String strangerToken = registerAndLogin("stranger-isolation@example.com", "Str0ng!Pass1");

        String createBody =
                objectMapper.writeValueAsString(
                        Map.of("title", "Bank", "username", "dave", "password", "BankSecret1!"));
        String createJson =
                mockMvc.perform(
                                post("/api/vault")
                                        .header("Authorization", "Bearer " + ownerToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(createBody))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long credentialId = objectMapper.readTree(createJson).path("data").path("id").asLong();

        mockMvc.perform(
                        get("/api/vault/" + credentialId)
                                .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
        mockMvc.perform(
                        delete("/api/vault/" + credentialId)
                                .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());
    }
}
