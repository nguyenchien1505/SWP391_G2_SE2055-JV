package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.AssignTaskRequest;
import com.example.SWP391_G2_SE2055_JV.dto.AssignableStaffResponse;
import com.example.SWP391_G2_SE2055_JV.dto.HousekeepingTaskResponse;
import com.example.SWP391_G2_SE2055_JV.dto.InspectTaskRequest;
import com.example.SWP391_G2_SE2055_JV.dto.InspectionRecordResponse;
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
 * <p>Không có API tạo hay hủy tay việc dọn: hệ thống tự sinh khi phòng chuyển "Chờ dọn"
 * (BR-HK-01) và tự hủy khi phòng bị khóa (BR-HK-09) — xem {@code HousekeepingRoomHooks}. Việc dọn
 * hằng ngày (tạo tay, tạo hàng loạt, hủy tay) đã bỏ từ V4.
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
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(housekeepingService.getTasks(status, taskType, assignedDate, from, to, pageable));
    }

    /**
     * S-10 — nhân viên dọn phòng gán được việc trong ngày {@code date}: đang làm việc, thuộc
     * Location của Manager, có quyền Dọn dẹp, và CÓ CA ngày đó (BR-HK-03).
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

    /**
     * Giao việc cho một hoặc nhiều người. Việc chưa phân công: nhóm bắt đầu dọn; việc đang làm:
     * thêm người vào nhóm (cùng ngày).
     */
    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','MANAGER')")
    public ResponseEntity<HousekeepingTaskResponse> assignTask(
            @PathVariable UUID id,
            @Valid @RequestBody AssignTaskRequest request) {
        return ResponseEntity.ok(housekeepingService.assignTask(id, request));
    }

    /**
     * Gỡ người khỏi việc đang làm.
     *
     * @param staffId người cần gỡ; bỏ trống = gỡ cả nhóm. Không còn ai thì việc về hàng chờ và phòng
     *                về «Chờ dọn».
     */
    @PatchMapping("/{id}/unassign")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','MANAGER')")
    public ResponseEntity<HousekeepingTaskResponse> unassignTask(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID staffId) {
        return ResponseEntity.ok(housekeepingService.unassignTask(id, staffId));
    }

    /**
     * Quyền sở hữu (chỉ người trong nhóm dọn) kiểm tra ở service — BR-PERM-05.
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

    /**
     * S-13 — Manager nghiệm thu phòng sau khi nhân viên báo dọn xong (BR-HK-06, BR-HK-08).
     * Trả <b>201</b> vì mỗi lần kiểm tra sinh ra một biên bản mới.
     */
    @PostMapping("/{id}/inspection")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','MANAGER')")
    public ResponseEntity<InspectionRecordResponse> inspectTask(
            @PathVariable UUID id,
            @Valid @RequestBody InspectTaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(housekeepingService.inspectTask(id, request));
    }

    /** Biên bản kiểm tra của một task — hiện lý do «không đạt» trên thẻ việc dọn lại (BR-HK-12). */
    @GetMapping("/{id}/inspection")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER','STAFF')")
    public ResponseEntity<InspectionRecordResponse> getInspection(@PathVariable UUID id) {
        return ResponseEntity.ok(housekeepingService.getInspection(id));
    }
}
