package com.securevault.security.dto;

import java.util.List;

/** backupCodes shown exactly once, at the moment MFA is enabled — never retrievable again. */
public record MfaVerifyResponse(List<String> backupCodes) {}
