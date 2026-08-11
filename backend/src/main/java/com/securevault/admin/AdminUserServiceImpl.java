package com.securevault.admin;

import com.securevault.admin.dto.AdminUserResponse;
import com.securevault.admin.dto.AdminUserStatusUpdateRequest;
import com.securevault.common.exception.UserNotFoundException;
import com.securevault.common.response.PagedResponse;
import com.securevault.user.User;
import com.securevault.user.UserRepository;
import com.securevault.user.UserSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;

    @Override
    public PagedResponse<AdminUserResponse> list(int page, int size, String search) {
        Specification<User> spec = (root, query, cb) -> cb.conjunction();
        if (search != null && !search.isBlank()) {
            spec = spec.and(UserSpecifications.emailOrNameContains(search));
        }
        Page<User> result =
                userRepository.findAll(
                        spec,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new PagedResponse<>(
                result.getContent().stream().map(AdminUserServiceImpl::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast(),
                result.hasNext());
    }

    @Override
    @Transactional
    public AdminUserResponse updateStatus(Long userId, AdminUserStatusUpdateRequest request) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new UserNotFoundException(userId));
        user.setAccountLocked(request.locked());
        if (!request.locked()) {
            // Activating also clears the failure counter (P5.5 precedent) — an admin unlock
            // means "start clean," not "still 5 failures away from re-locking immediately."
            user.setFailedLoginAttempts(0);
        }
        userRepository.save(user);
        log.info("Admin updated user status: userId={}, locked={}", userId, request.locked());
        return toResponse(user);
    }

    private static AdminUserResponse toResponse(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.isAccountLocked(),
                user.isMfaEnabled(),
                user.getCreatedAt());
    }
}
