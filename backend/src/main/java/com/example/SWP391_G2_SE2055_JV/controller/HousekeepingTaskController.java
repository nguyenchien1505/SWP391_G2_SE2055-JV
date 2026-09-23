package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.AssignTaskRequest;
import com.example.SWP391_G2_SE2055_JV.dto.AssignableStaffResponse;
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
import java.util.List;
import java.util.UUID;

/**
 * Lịch dọn phòng — BR-PERM-03 (Manager tạo/điều chỉnh lịch dọn, assign task),
 * BR-PERM-05 (nhân viên Dọn dẹp nhận task và bấm hoàn thành).
 *
 * <p>Không có API tạo task CHECKOUT: hệ thống tự sinh khi phòng chuyển "Chờ dọn"
 * (BR-HK-01) — xem {@code HousekeepingRoomHooks}. API kiểm tra phòng sau dọn (BR-HK-06)
 * thuộc F6, chưa có.
 *
 * <p>Rule URL trong {@code SecurityConfig}: {@code GET /housekeeping/**} cho cả 4 role,
 * {@code PATCH /housekeeping/tasks/*}{@code /complete} thêm {@code POSITION_HOUSEKEEPING},
 * các path còn lại chỉ Admin và Manager.
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

    /**
     * S-10 — nhân viên dọn phòng gán được việc trong ngày {@code date}: đang làm việc, thuộc
     * Location của Manager, Position loại Dọn dẹp, và CÓ CA ngày đó (BR-HK-03).
     *
     * <p>Không nhận {@code locationId}: phạm vi luôn là Location của người đang đăng nhập.
     * Khai báo trước {@code /{id}} cho dễ đọc — Spring vẫn ưu tiên path cố định hơn path có biến.
     */
    @GetMapping("/assignable-staff")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','MANAGER')")
    public ResponseEntity<List<AssignableStaffResponse>> getAssignableStaff(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(housekeepingService.getAssignableStaff(date));
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

    /**
     * Quyền sở hữu (chỉ người được phân công) kiểm tra ở service — BR-PERM-05.
     *
     * <p>Q7 (chốt 22/09/2026): bỏ {@code MANAGER} khỏi đây. BR-PERM-05 chỉ nói nhân viên Dọn
     * dẹp, và service vốn đã chặn mọi người không phải người được gán — để MANAGER ở lại chỉ
     * khiến Manager bấm xong nhận 400 thay vì 403, tức là hứa một quyền không có thật.
     */
    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN') or hasAuthority('POSITION_HOUSEKEEPING')")
    public ResponseEntity<HousekeepingTaskResponse> completeTask(@PathVariable UUID id) {
        return ResponseEntity.ok(housekeepingService.completeTask(id));
    }
}
