package com.securevault.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Not yet thrown anywhere in Phase 1-2 code — login intentionally reports
 * InvalidCredentialsException instead so it never discloses whether an email exists (S1.2). Defined
 * now, per P2.3/M-26, ready for the direct user-lookup endpoints later phases (e.g. admin console,
 * S5.8) will add.
 */
public class UserNotFoundException extends BusinessException {

    public UserNotFoundException(Long id) {
        super(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND, "User not found: " + id);
    }
}
