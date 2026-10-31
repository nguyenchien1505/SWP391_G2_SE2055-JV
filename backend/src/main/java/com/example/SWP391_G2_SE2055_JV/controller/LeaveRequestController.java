package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.ApproveLeaveRequestRequest;
import com.example.SWP391_G2_SE2055_JV.dto.CreateLeaveRequestRequest;
import com.example.SWP391_G2_SE2055_JV.dto.LeaveApprovalResponse;
import com.example.SWP391_G2_SE2055_JV.dto.LeaveBalanceResponse;
import com.example.SWP391_G2_SE2055_JV.dto.LeaveRequestResponse;
import com.example.SWP391_G2_SE2055_JV.dto.RejectLeaveRequestRequest;
import com.example.SWP391_G2_SE2055_JV.dto.RevokeLeaveRequestRequest;
import com.example.SWP391_G2_SE2055_JV.enums.LeaveRequestStatus;
import com.example.SWP391_G2_SE2055_JV.service.LeaveRequestService;
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

import java.util.List;
import java.util.UUID;

/**
 * Đơn xin nghỉ — BR-SCH-06..08, BR-SCH-16, BR-SCH-23, BR-SCH-27..31 (BR Milestone 2).
 *
 * <p>Người gửi (Staff, Manager) chỉ làm việc với đơn của chính mình. Người duyệt theo cấp (BR-SCH-08): Manager
 * duyệt đơn Staff khách sạn mình, Giám đốc duyệt đơn Manager — chặn theo ROLE nên Giám sát vận hành (Staff)
 * không duyệt được (BR-PERM-07). Rule URL trong {@code SecurityConfig}: duyệt / từ chối / hủy phần còn lại / xem
 * hàng duyệt chỉ Admin, Giám đốc, Manager; các path còn lại cho cả 4 role, phân tiếp ở {@code @PreAuthorize}.
 */
@RestController
@RequestMapping("/scheduling/leave-requests")
@RequiredArgsConstructor
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;

    /** Gửi đơn cho chính mình. Luật theo ngày (báo trước, quỹ phép, trùng đơn) lỗi thì 400 kèm lý do. */
    @PostMapping
    @PreAuthorize("hasAnyRole('STAFF','MANAGER')")
    public ResponseEntity<LeaveRequestResponse> createLeaveRequest(
            @Valid @RequestBody CreateLeaveRequestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(leaveRequestService.createLeaveRequest(request));
    }

    /** Đơn của chính mình, mới gửi trước. Không truyền {@code status} = mọi trạng thái. */
    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('STAFF','MANAGER')")
    public ResponseEntity<Page<LeaveRequestResponse>> getMyLeaveRequests(
            @RequestParam(required = false) LeaveRequestStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(leaveRequestService.getMyLeaveRequests(status, pageable));
    }

    /** Quỹ phép năm của chính mình trong năm nay — BR-SCH-27, BR-SCH-28. */
    @GetMapping("/balance")
    @PreAuthorize("hasAnyRole('STAFF','MANAGER')")
    public ResponseEntity<LeaveBalanceResponse> getMyBalance() {
        return ResponseEntity.ok(leaveRequestService.getMyBalance());
    }

    /** BR-SCH-16: người gửi tự hủy khi đơn còn chờ duyệt. */
    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('STAFF','MANAGER')")
    public ResponseEntity<LeaveRequestResponse> cancelLeaveRequest(@PathVariable UUID id) {
        return ResponseEntity.ok(leaveRequestService.cancelLeaveRequest(id));
    }

    /**
     * Hàng đơn cần duyệt — Manager: đơn Staff khách sạn mình; Giám đốc: đơn Manager. Không truyền
     * {@code status} = mọi trạng thái.
     */
    @GetMapping("/review")
    @PreAuthorize("hasAnyRole('MANAGER','DIRECTOR')")
    public ResponseEntity<Page<LeaveRequestResponse>> getReviewQueue(
            @RequestParam(required = false) LeaveRequestStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(leaveRequestService.getReviewQueue(status, pageable));
    }

    /**
     * Xem trước hệ quả của việc duyệt: ca sẽ gỡ, ca giữ, ca sẽ mất lễ tân duy nhất kèm các lễ tân xếp thay được
     * (BR-SCH-29 sửa 31/10/2026), quỹ phép năm.
     */
    @GetMapping("/{id}/approval-preview")
    @PreAuthorize("hasAnyRole('MANAGER','DIRECTOR')")
    public ResponseEntity<LeaveApprovalResponse> previewApproval(@PathVariable UUID id) {
        return ResponseEntity.ok(leaveRequestService.previewApproval(id));
    }

    /**
     * Duyệt đơn. Ca theo mẫu sẽ mất lễ tân duy nhất thì phải gửi kèm lễ tân thay cho từng ca (BR-SCH-29 sửa
     * 31/10/2026), chọn trong danh sách của {@code approval-preview}; đơn không làm ca nào thiếu lễ tân thì không cần
     * body.
     */
    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('MANAGER','DIRECTOR')")
    public ResponseEntity<LeaveApprovalResponse> approveLeaveRequest(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ApproveLeaveRequestRequest request) {
        return ResponseEntity.ok(leaveRequestService.approveLeaveRequest(id,
            request == null ? List.of() : request.getReplacements()));
    }

    /** Từ chối đơn đang chờ duyệt, bắt buộc lý do — người gửi đọc được (chốt 31/10/2026). Lịch làm việc không đổi. */
    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('MANAGER','DIRECTOR')")
    public ResponseEntity<LeaveRequestResponse> rejectLeaveRequest(
            @PathVariable UUID id,
            @Valid @RequestBody RejectLeaveRequestRequest request) {
        return ResponseEntity.ok(leaveRequestService.rejectLeaveRequest(id, request.getReason()));
    }

    /** BR-SCH-16 bản sửa 28/10/2026: người duyệt hủy phần ngày chưa tới của đơn đã duyệt, bắt buộc lý do. */
    @PatchMapping("/{id}/revoke")
    @PreAuthorize("hasAnyRole('MANAGER','DIRECTOR')")
    public ResponseEntity<LeaveRequestResponse> revokeLeaveRequest(
            @PathVariable UUID id,
            @Valid @RequestBody RevokeLeaveRequestRequest request) {
        return ResponseEntity.ok(leaveRequestService.revokeLeaveRequest(id, request.getReason()));
    }
}
