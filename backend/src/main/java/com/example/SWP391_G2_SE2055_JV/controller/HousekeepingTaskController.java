package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.AssignTaskRequest;
import com.example.SWP391_G2_SE2055_JV.dto.CreateStayoverTaskRequest;
import com.example.SWP391_G2_SE2055_JV.dto.HousekeepingTaskResponse;
import com.example.SWP391_G2_SE2055_JV.enums.HousekeepingTaskStatus;
import com.example.SWP391_G2_SE2055_JV.enums.HousekeepingTaskType;
import com.example.SWP391_G2_SE2055_JV.service.HousekeepingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Lịch dọn phòng — BR-PERM-03 (Manager tạo/điều chỉnh lịch dọn, assign task),
 * BR-PERM-05 (nhân viên Dọn dẹp nhận task và bấm hoàn thành).
 *
 * <p>Không có API tạo task CHECKOUT: hệ thống tự sinh khi phòng chuyển "Chờ dọn"
 * (BR-HK-01) — xem {@code HousekeepingRoomHooks}. Chưa có API kiểm tra phòng sau dọn:
 * bước đó đổi trạng thái phòng, chờ tích hợp module Quản lý phòng.
 */
@RestController
@RequestMapping("/housekeeping/tasks")
@RequiredArgsConstructor
public class HousekeepingTaskController {

    private final HousekeepingService housekeepingService;

    /** Nhân viên chỉ nhận về task của chính mình — lọc ở service. */
    @GetMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER','STAFF')")
    public ResponseEntity<Page<HousekeepingTaskResponse>> getTasks(
            @RequestParam(required = false) HousekeepingTaskStatus status,
            @RequestParam(required = false) HousekeepingTaskType taskType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate assignedDate,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(housekeepingService.getTasks(status, taskType, assignedDate, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER','STAFF')")
    public ResponseEntity<HousekeepingTaskResponse> getTask(@PathVariable UUID id) {
        return ResponseEntity.ok(housekeepingService.getTask(id));
    }

    /** Chỉ tạo tay task STAYOVER — BR-HK-05. */
    @PostMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','MANAGER')")
    public ResponseEntity<HousekeepingTaskResponse> createStayoverTask(
            @Valid @RequestBody CreateStayoverTaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(housekeepingService.createStayoverTask(request));
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','MANAGER')")
    public ResponseEntity<HousekeepingTaskResponse> assignTask(
            @PathVariable UUID id,
            @Valid @RequestBody AssignTaskRequest request) {
        return ResponseEntity.ok(housekeepingService.assignTask(id, request));
    }

    @PatchMapping("/{id}/unassign")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','MANAGER')")
    public ResponseEntity<HousekeepingTaskResponse> unassignTask(@PathVariable UUID id) {
        return ResponseEntity.ok(housekeepingService.unassignTask(id));
    }

    /** Quyền sở hữu (chỉ người được phân công) kiểm tra ở service — BR-PERM-05. */
    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','MANAGER') or hasAuthority('POSITION_HOUSEKEEPING')")
    public ResponseEntity<HousekeepingTaskResponse> completeTask(@PathVariable UUID id) {
        return ResponseEntity.ok(housekeepingService.completeTask(id));
    }
}
