package com.example.SWP391_G2_SE2055_JV.scheduling.controller;

import com.example.SWP391_G2_SE2055_JV.scheduling.dto.CreateWorkSchedulePolicyRequest;
import com.example.SWP391_G2_SE2055_JV.scheduling.dto.UpdateWorkSchedulePolicyRequest;
import com.example.SWP391_G2_SE2055_JV.scheduling.dto.WorkSchedulePolicyResponse;
import com.example.SWP391_G2_SE2055_JV.scheduling.service.WorkSchedulePolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Work Schedule Management — general policy (max shifts/week, min rest hours, etc.)
 * that Director/Manager set per location; {@code ShiftService} enforces it when a
 * shift is created.
 *
 * Authorization:
 * - ADMIN_PLATFORM, DIRECTOR, MANAGER: full access (create, read, update, delete)
 * - Other roles: no access (policies aren't staff-facing)
 */
@RestController
@RequestMapping("/scheduling/policies")
@RequiredArgsConstructor
public class WorkSchedulePolicyController {

    private final WorkSchedulePolicyService policyService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_PLATFORM', 'DIRECTOR', 'MANAGER')")
    public ResponseEntity<Page<WorkSchedulePolicyResponse>> getPolicies(
            @RequestParam(required = false) Long locationId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(policyService.getPolicies(locationId, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_PLATFORM', 'DIRECTOR', 'MANAGER')")
    public ResponseEntity<WorkSchedulePolicyResponse> getPolicyById(@PathVariable Long id) {
        return ResponseEntity.ok(policyService.getPolicyById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_PLATFORM', 'DIRECTOR', 'MANAGER')")
    public ResponseEntity<WorkSchedulePolicyResponse> createPolicy(
            @Valid @RequestBody CreateWorkSchedulePolicyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(policyService.createPolicy(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_PLATFORM', 'DIRECTOR', 'MANAGER')")
    public ResponseEntity<WorkSchedulePolicyResponse> updatePolicy(
            @PathVariable Long id,
            @Valid @RequestBody UpdateWorkSchedulePolicyRequest request) {
        return ResponseEntity.ok(policyService.updatePolicy(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_PLATFORM', 'DIRECTOR', 'MANAGER')")
    public ResponseEntity<Void> deletePolicy(@PathVariable Long id) {
        policyService.deletePolicy(id);
        return ResponseEntity.noContent().build();
    }
}
