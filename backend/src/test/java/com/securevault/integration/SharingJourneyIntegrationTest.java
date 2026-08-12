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

/**
 * P7.2 journey 3: owner grants READ, recipient reads, recipient's update is rejected, owner
 * upgrades to EDIT, recipient updates, owner revokes, recipient is denied — the full S5.1
 * permission matrix driven through the real HTTP layer against a real Postgres container.
 */
class SharingJourneyIntegrationTest extends AbstractIntegrationTest {

    @Test
    void should_walkTheFullSharingPermissionMatrix() throws Exception {
        String ownerToken = registerAndLogin("share-owner@example.com", "Str0ng!Pass1");
        String recipientToken = registerAndLogin("share-recipient@example.com", "Str0ng!Pass1");

        String createBody =
                objectMapper.writeValueAsString(
                        Map.of(
                                "title",
                                "Shared Site",
                                "username",
                                "dave",
                                "password",
                                "OwnerSecret1!"));
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

        // Owner grants READ.
        String shareBody =
                objectMapper.writeValueAsString(
                        Map.of(
                                "credentialId", credentialId,
                                "sharedWithEmail", "share-recipient@example.com",
                                "permission", "READ"));
        String shareJson =
                mockMvc.perform(
                                post("/api/share")
                                        .header("Authorization", "Bearer " + ownerToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(shareBody))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.data.permission").value("READ"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long shareId = objectMapper.readTree(shareJson).path("data").path("id").asLong();

        // Recipient can read the decrypted password through a READ share.
        mockMvc.perform(
                        get("/api/vault/" + credentialId)
                                .header("Authorization", "Bearer " + recipientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.password").value("OwnerSecret1!"));

        // A READ-only recipient cannot update.
        String updateAttempt = objectMapper.writeValueAsString(Map.of("title", "Hijacked"));
        mockMvc.perform(
                        put("/api/vault/" + credentialId)
                                .header("Authorization", "Bearer " + recipientToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updateAttempt))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        // A READ-only recipient cannot delete or reshare either.
        mockMvc.perform(
                        delete("/api/vault/" + credentialId)
                                .header("Authorization", "Bearer " + recipientToken))
                .andExpect(status().isForbidden());

        // Owner upgrades to EDIT.
        String upgradeBody = objectMapper.writeValueAsString(Map.of("permission", "EDIT"));
        mockMvc.perform(
                        put("/api/share/" + shareId)
                                .header("Authorization", "Bearer " + ownerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(upgradeBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permission").value("EDIT"));

        // Now the recipient can update.
        String realUpdate =
                objectMapper.writeValueAsString(Map.of("title", "Updated By Recipient"));
        mockMvc.perform(
                        put("/api/vault/" + credentialId)
                                .header("Authorization", "Bearer " + recipientToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(realUpdate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Updated By Recipient"));

        // ...but still cannot delete — EDIT never implies delete rights (M-45).
        mockMvc.perform(
                        delete("/api/vault/" + credentialId)
                                .header("Authorization", "Bearer " + recipientToken))
                .andExpect(status().isForbidden());

        // Owner revokes.
        mockMvc.perform(
                        delete("/api/share/" + shareId)
                                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        // Revocation takes effect immediately — no stale-cache window.
        mockMvc.perform(
                        get("/api/vault/" + credentialId)
                                .header("Authorization", "Bearer " + recipientToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_rejectSelfShareAndDuplicateShare() throws Exception {
        String ownerToken = registerAndLogin("self-share-owner@example.com", "Str0ng!Pass1");
        registerAndLogin("dup-share-recipient@example.com", "Str0ng!Pass1");

        String createBody =
                objectMapper.writeValueAsString(
                        Map.of("title", "Site", "username", "dave", "password", "Secret1!"));
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

        String selfShareBody =
                objectMapper.writeValueAsString(
                        Map.of(
                                "credentialId", credentialId,
                                "sharedWithEmail", "self-share-owner@example.com",
                                "permission", "READ"));
        mockMvc.perform(
                        post("/api/share")
                                .header("Authorization", "Bearer " + ownerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(selfShareBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SELF_SHARE_NOT_ALLOWED"));

        String shareBody =
                objectMapper.writeValueAsString(
                        Map.of(
                                "credentialId", credentialId,
                                "sharedWithEmail", "dup-share-recipient@example.com",
                                "permission", "READ"));
        mockMvc.perform(
                        post("/api/share")
                                .header("Authorization", "Bearer " + ownerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(shareBody))
                .andExpect(status().isCreated());
        mockMvc.perform(
                        post("/api/share")
                                .header("Authorization", "Bearer " + ownerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(shareBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SHARE_ALREADY_EXISTS"));
    }
}
