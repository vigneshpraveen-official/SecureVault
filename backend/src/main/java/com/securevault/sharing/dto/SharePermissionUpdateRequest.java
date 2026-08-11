package com.securevault.sharing.dto;

import com.securevault.sharing.SharePermission;
import jakarta.validation.constraints.NotNull;

public record SharePermissionUpdateRequest(@NotNull SharePermission permission) {}
