package com.securevault.common.response;

import java.util.List;

/**
 * Wraps a page of results inside ApiResponse.data (master §9, D-11). First consumed in S4.5 (GET
 * /api/vault) — field names match the mentor's exact spec (P4.5/M-34: "content, totalElements,
 * totalPages, currentPage, pageSize, plus first/last/hasNext"), not the generic page/size this
 * record had while unused since P2.3.
 */
public record PagedResponse<T>(
        List<T> content,
        int currentPage,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        boolean hasNext) {}
