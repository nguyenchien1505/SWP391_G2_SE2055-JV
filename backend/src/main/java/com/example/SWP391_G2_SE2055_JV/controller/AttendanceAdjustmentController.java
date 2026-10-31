package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.AttendanceAdjustmentResponse;
import com.example.SWP391_G2_SE2055_JV.dto.ClientInfo;
import com.example.SWP391_G2_SE2055_JV.dto.CreateAttendanceAdjustmentRequest;
import com.example.SWP391_G2_SE2055_JV.dto.ReviewAttendanceAdjustmentRequest;
import com.example.SWP391_G2_SE2055_JV.enums.AdjustmentRequestStatus;
import com.example.SWP391_G2_SE2055_JV.service.AttendanceAdjustmentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Đơn điều chỉnh công (chấm công M2): nhân viên gửi / hủy đơn cho ca của mình, quản lý khách sạn duyệt / từ chối.
 * Rule URL của SecurityConfig mở path này cho mọi người lao động; ai làm được gì phân ở {@code @PreAuthorize}.
 */
@RestController
@RequestMapping("/scheduling/attendance-adjustments")
@RequiredArgsConstructor
public class AttendanceAdjustmentController {

    private final AttendanceAdjustmentService adjustmentService;

    @PostMapping
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<AttendanceAdjustmentResponse> create(@Valid @RequestBody CreateAttendanceAdjustmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adjustmentService.create(request));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<Page<AttendanceAdjustmentResponse>> getMine(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(adjustmentService.getMine(pageable));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<AttendanceAdjustmentResponse> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(adjustmentService.cancel(id));
    }

    /** @param statuses bỏ trống là đơn đang chờ */
    @GetMapping("/review")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Page<AttendanceAdjustmentResponse>> getForReview(
            @RequestParam(required = false) List<AdjustmentRequestStatus> statuses,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(adjustmentService.getForReview(statuses, pageable));
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<AttendanceAdjustmentResponse> approve(
            @PathVariable UUID id, @Valid @RequestBody(required = false) ReviewAttendanceAdjustmentRequest request,
            HttpServletRequest http) {
        return ResponseEntity.ok(adjustmentService.approve(id, request == null ? null : request.getNote(),
            ClientInfo.from(http)));
    }

    /** Lý do từ chối bắt buộc — nhân viên đọc được. */
    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<AttendanceAdjustmentResponse> reject(
            @PathVariable UUID id, @Valid @RequestBody(required = false) ReviewAttendanceAdjustmentRequest request) {
        return ResponseEntity.ok(adjustmentService.reject(id, request == null ? null : request.getNote()));
    }
}
