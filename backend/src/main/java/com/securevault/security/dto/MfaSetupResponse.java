package com.securevault.security.dto;

/** secret is the manual-entry fallback — shown only this once, same treatment as backup codes. */
public record MfaSetupResponse(String secret, String otpauthUri, String qrCodeDataUri) {}
