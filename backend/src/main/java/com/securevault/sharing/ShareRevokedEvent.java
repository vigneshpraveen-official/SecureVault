package com.securevault.sharing;

/**
 * Published on revoke (P5.6 trigger: "share revoked") — sharedWithUserId is the one who lost
 * access.
 */
public record ShareRevokedEvent(Long sharedWithUserId, String credentialTitle) {}
