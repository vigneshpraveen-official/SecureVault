package com.securevault.monitoring;

import com.securevault.common.response.ApiResponse;
import com.securevault.monitoring.dto.DeviceResponse;
import com.securevault.security.UserPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitoring/devices")
@RequiredArgsConstructor
@Tag(name = "Monitoring", description = "Login attempts, security alerts, risk score, and devices")
public class DeviceController {

    private final DeviceService deviceService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DeviceResponse>>> list(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(
                ApiResponse.success("Devices retrieved", deviceService.list(principal.getId())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        deviceService.revoke(id, principal.getId());
        return ResponseEntity.noContent().build();
    }
}
