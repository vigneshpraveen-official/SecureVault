package com.securevault.admin;

import com.securevault.admin.dto.AdminUserResponse;
import com.securevault.admin.dto.AdminUserStatusUpdateRequest;
import com.securevault.common.response.PagedResponse;

public interface AdminUserService {

    PagedResponse<AdminUserResponse> list(int page, int size, String search);

    AdminUserResponse updateStatus(Long userId, AdminUserStatusUpdateRequest request);
}
