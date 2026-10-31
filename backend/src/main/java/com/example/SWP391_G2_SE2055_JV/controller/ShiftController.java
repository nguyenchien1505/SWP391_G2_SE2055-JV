package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.AssignShiftRequest;
import com.example.SWP391_G2_SE2055_JV.dto.AttendanceEventResponse;
import com.example.SWP391_G2_SE2055_JV.dto.CheckInOutRequest;
import com.example.SWP391_G2_SE2055_JV.dto.ClientInfo;
import com.example.SWP391_G2_SE2055_JV.dto.CreateShiftBatchRequest;
import com.example.SWP391_G2_SE2055_JV.dto.CreateShiftRequest;
import com.example.SWP391_G2_SE2055_JV.dto.ManagerAttendanceRequest;
import com.example.SWP391_G2_SE2055_JV.dto.ShiftResponse;
import com.example.SWP391_G2_SE2055_JV.dto.UpdateShiftRequest;
import com.example.SWP391_G2_SE2055_JV.enums.UnassignedReason;
import com.example.SWP391_G2_SE2055_JV.service.AttendanceService;
import com.example.SWP391_G2_SE2055_JV.service.ShiftService;
import jakarta.servlet.http.HttpServletRequest;
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
 * Lịch làm việc — BR-PERM-03 (Manager xếp ca), BR-PERM-04/05/06 (mọi người lao động
 * xem lịch cá nhân và check-in/check-out).
 */
@RestController
@RequestMapping("/scheduling/shifts")
@RequiredArgsConstructor
public class ShiftController {

    private final ShiftService      shiftService;
    private final AttendanceService attendanceService;

    /**
     * Staff chỉ nhận về ca của chính mình — lọc ở service.
     *
     * @param from cùng {@code to}: chỉ lấy ca có ngày bắt đầu trong khoảng này (tính cả hai đầu),
     *             ví dụ một tuần của bảng xếp lịch. Bỏ trống cả hai thì không lọc theo ngày.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER','STAFF')")
    public ResponseEntity<Page<ShiftResponse>> getShifts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "shiftDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(shiftService.getShifts(from, to, pageable));
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

    /**
     * Giao cùng một ca cho nhiều người: mỗi người một ca riêng (DM-03), kiểm tra Schedule Policy
     * từng người. Có người vi phạm thì không lưu ca nào, response 400 có {@code violations} nêu lý
     * do của từng người.
     */
    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','MANAGER')")
    public ResponseEntity<List<ShiftResponse>> createShifts(@Valid @RequestBody CreateShiftBatchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shiftService.createShifts(request));
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

    /**
     * Nhân viên vào ca của chính mình — từ 30 phút trước giờ bắt đầu tới hết ca (chấm công M2). Body không bắt
     * buộc: giao diện cũ không gửi gì vẫn chạy; trường {@code verification} để trống ở M2.
     */
    @PostMapping("/{id}/check-in")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER','STAFF')")
    public ResponseEntity<ShiftResponse> checkIn(@PathVariable UUID id,
                                                 @Valid @RequestBody(required = false) CheckInOutRequest request,
                                                 HttpServletRequest http) {
        return ResponseEntity.ok(attendanceService.checkIn(id, request, ClientInfo.from(http)));
    }

    /** Kết thúc ca — từ lúc vào ca tới hết ca + 4 giờ. */
    @PostMapping("/{id}/check-out")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER','STAFF')")
    public ResponseEntity<ShiftResponse> checkOut(@PathVariable UUID id,
                                                  @Valid @RequestBody(required = false) CheckInOutRequest request,
                                                  HttpServletRequest http) {
        return ResponseEntity.ok(attendanceService.checkOut(id, request, ClientInfo.from(http)));
    }

    /** Manager nhập bù / sửa giờ vào – ra ca ở khách sạn mình, bắt buộc lý do. */
    @PutMapping("/{id}/attendance")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','MANAGER')")
    public ResponseEntity<ShiftResponse> updateAttendance(@PathVariable UUID id,
                                                          @Valid @RequestBody ManagerAttendanceRequest request,
                                                          HttpServletRequest http) {
        return ResponseEntity.ok(attendanceService.updateAttendance(id, request, ClientInfo.from(http)));
    }

    /** Mọi lần ghi chấm công của ca (tự bấm, Manager nhập) kèm IP / thiết bị — để đối soát. */
    @GetMapping("/{id}/attendance-events")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER')")
    public ResponseEntity<List<AttendanceEventResponse>> getAttendanceEvents(@PathVariable UUID id) {
        return ResponseEntity.ok(attendanceService.getEvents(id));
    }
}
