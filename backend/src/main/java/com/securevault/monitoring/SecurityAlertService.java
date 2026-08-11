package com.securevault.monitoring;

import com.securevault.monitoring.dto.SecurityAlertResponse;
import java.util.List;

public interface SecurityAlertService {

    void raise(Long userId, AlertType type, AlertSeverity severity, String message);

    List<SecurityAlertResponse> listForUser(Long userId);

    List<SecurityAlertResponse> listAll();
}
