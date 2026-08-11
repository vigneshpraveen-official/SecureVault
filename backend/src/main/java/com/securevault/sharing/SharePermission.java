package com.securevault.sharing;

/**
 * M-42 permission semantics: READ = view only. EDIT = view and update. Neither can delete or
 * reshare.
 */
public enum SharePermission {
    READ,
    EDIT
}
