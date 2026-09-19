package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.SchedulePolicyResponse;
import com.example.SWP391_G2_SE2055_JV.dto.UpdateSchedulePolicyRequest;
import com.example.SWP391_G2_SE2055_JV.service.SchedulePolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BR-SCH-01 + DM-18: một Tenant có đúng MỘT Schedule Policy, nên đây là tài nguyên
 * đơn lẻ ({@code /scheduling/policy}) chứ không phải collection — khác hẳn API cũ
 * {@code /scheduling/policies} vốn cho phép nhiều policy theo Location.
 */
@RestController
@RequestMapping("/scheduling/policy")
@RequiredArgsConstructor
public class SchedulePolicyController {

    private final SchedulePolicyService schedulePolicyService;

    /** Manager cần đọc policy để hiểu vì sao một ca bị chặn, nên được phép xem. */
    @GetMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER')")
    public ResponseEntity<SchedulePolicyResponse> getPolicy() {
        return ResponseEntity.ok(schedulePolicyService.getPolicy());
    }

    /** Chỉ Giám đốc được sửa — BR-SCH-01, BR-PERM-02. */
    @PutMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR')")
    public ResponseEntity<SchedulePolicyResponse> updatePolicy(
            @Valid @RequestBody UpdateSchedulePolicyRequest request) {
        return ResponseEntity.ok(schedulePolicyService.updatePolicy(request));
    }
}
