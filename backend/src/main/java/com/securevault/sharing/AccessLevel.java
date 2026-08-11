package com.securevault.sharing;

/**
 * What a given user may do with a given credential, resolved by {@link AccessEvaluator} — the
 * single authorisation decision point every vault operation goes through (P5.1/M-44).
 */
public enum AccessLevel {
    OWNER,
    EDIT,
    READ,
    NONE
}
