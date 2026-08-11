package com.securevault.admin.dto;

import jakarta.validation.constraints.NotNull;

/** true = lock the account; false = activate (unlock and reset the failed-attempt counter). */
public record AdminUserStatusUpdateRequest(@NotNull Boolean locked) {}
