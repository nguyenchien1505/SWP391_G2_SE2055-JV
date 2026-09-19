package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.AssignShiftRequest;
import com.example.SWP391_G2_SE2055_JV.dto.CreateShiftRequest;
import com.example.SWP391_G2_SE2055_JV.dto.ShiftResponse;
import com.example.SWP391_G2_SE2055_JV.dto.UpdateShiftRequest;
import com.example.SWP391_G2_SE2055_JV.enums.UnassignedReason;
import com.example.SWP391_G2_SE2055_JV.service.ShiftService;
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

import java.util.UUID;

/**
 * Lịch làm việc — BR-PERM-03 (Manager xếp ca), BR-PERM-04/05/06 (mọi người lao động
 * xem lịch cá nhân và check-in/check-out).
 */
@RestController
@RequestMapping("/scheduling/shifts")
@RequiredArgsConstructor
public class ShiftController {

    private final ShiftService shiftService;

    /** Staff chỉ nhận về ca của chính mình — lọc ở service. */
    @GetMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER','STAFF')")
    public ResponseEntity<Page<ShiftResponse>> getShifts(
            @PageableDefault(size = 20, sort = "shiftDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(shiftService.getShifts(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER','STAFF')")
    public ResponseEntity<ShiftResponse> getShiftById(@PathVariable UUID id) {
        return ResponseEntity.ok(shiftService.getShiftById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','MANAGER')")
    public ResponseEntity<ShiftResponse> createShift(@Valid @RequestBody CreateShiftRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shiftService.createShift(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','MANAGER')")
    public ResponseEntity<ShiftResponse> updateShift(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateShiftRequest request) {
        return ResponseEntity.ok(shiftService.updateShift(id, request));
    }

    /** Gán người cho ca chưa phân công. Chạy lại toàn bộ kiểm tra Schedule Policy. */
    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','MANAGER')")
    public ResponseEntity<ShiftResponse> assignStaff(
            @PathVariable UUID id,
            @Valid @RequestBody AssignShiftRequest request) {
        return ResponseEntity.ok(shiftService.assignStaff(id, request.getStaffId()));
    }

    /**
     * Gỡ người khỏi ca. Manager gỡ tay thì lý do là MANAGER_MANUAL; ba lý do còn lại
     * (TRANSFER, TERMINATION, LEAVE_APPROVED) do hệ thống tự đặt — BR-SCH-24.
     */
    @PatchMapping("/{id}/unassign")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','MANAGER')")
    public ResponseEntity<ShiftResponse> unassignStaff(@PathVariable UUID id) {
        return ResponseEntity.ok(shiftService.unassignStaff(id, UnassignedReason.MANAGER_MANUAL));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','MANAGER')")
    public ResponseEntity<Void> deleteShift(@PathVariable UUID id) {
        shiftService.deleteShift(id);
        return ResponseEntity.noContent().build();
    }

    /** BR-DASH-01: chỉ ghi timestamp, Milestone 1 không tính đi muộn/về sớm. */
    @PostMapping("/{id}/check-in")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER','STAFF')")
    public ResponseEntity<ShiftResponse> checkIn(@PathVariable UUID id) {
        return ResponseEntity.ok(shiftService.checkIn(id));
    }

    @PostMapping("/{id}/check-out")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER','STAFF')")
    public ResponseEntity<ShiftResponse> checkOut(@PathVariable UUID id) {
        return ResponseEntity.ok(shiftService.checkOut(id));
    }
}
