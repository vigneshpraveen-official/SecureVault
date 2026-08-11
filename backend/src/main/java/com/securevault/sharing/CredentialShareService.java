package com.securevault.sharing;

import com.securevault.sharing.dto.ShareCreateRequest;
import com.securevault.sharing.dto.SharePermissionUpdateRequest;
import com.securevault.sharing.dto.ShareResponse;
import java.util.List;

public interface CredentialShareService {

    ShareResponse create(Long ownerId, ShareCreateRequest request);

    List<ShareResponse> received(Long userId);

    List<ShareResponse> sent(Long ownerId);

    ShareResponse updatePermission(
            Long shareId, Long ownerId, SharePermissionUpdateRequest request);

    void revoke(Long shareId, Long ownerId);
}
