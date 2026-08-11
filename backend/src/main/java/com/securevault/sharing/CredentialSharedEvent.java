package com.securevault.sharing;

/** Published on a successful share (P5.6 trigger: "credential shared with you"). */
public record CredentialSharedEvent(
        Long sharedWithUserId,
        String ownerEmail,
        String credentialTitle,
        SharePermission permission) {}
