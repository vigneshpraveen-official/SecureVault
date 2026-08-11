package com.securevault.common.response;

import java.util.List;

/**
 * Wraps a page of results inside ApiResponse.data (master §9, D-11). Not consumed anywhere yet —
 * added now per P2.3 so it exists ahead of S4.5's pagination/sorting/filtering work, which is the
 * first endpoint that will actually return one.
 */
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {}
