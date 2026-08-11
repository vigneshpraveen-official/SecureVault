package com.securevault.monitoring;

import com.securevault.monitoring.dto.SecurityAlertResponse;
import com.securevault.user.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityAlertServiceImpl implements SecurityAlertService {

    private final SecurityAlertRepository securityAlertRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void raise(Long userId, AlertType type, AlertSeverity severity, String message) {
        SecurityAlert alert =
                SecurityAlert.builder()
                        .user(userRepository.getReferenceById(userId))
                        .type(type)
                        .severity(severity)
                        .message(message)
                        .resolved(false)
                        .build();
        SecurityAlert saved = securityAlertRepository.save(alert);
        log.warn(
                "Security alert raised: userId={}, type={}, severity={}, message={}",
                userId,
                type,
                severity,
                message);
        eventPublisher.publishEvent(
                new SecurityAlertRaisedEvent(saved.getId(), userId, type, severity, message));
    }

    @Override
    public List<SecurityAlertResponse> listForUser(Long userId) {
        return securityAlertRepository
                .findByUserIdAndResolvedFalseOrderByCreatedAtDesc(userId)
                .stream()
                .map(SecurityAlertServiceImpl::toResponse)
                .toList();
    }

    @Override
    public List<SecurityAlertResponse> listAll() {
        return securityAlertRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(SecurityAlertServiceImpl::toResponse)
                .toList();
    }

    private static SecurityAlertResponse toResponse(SecurityAlert alert) {
        return new SecurityAlertResponse(
                alert.getId(),
                alert.getType(),
                alert.getSeverity(),
                alert.getMessage(),
                alert.isResolved(),
                alert.getCreatedAt());
    }
}
