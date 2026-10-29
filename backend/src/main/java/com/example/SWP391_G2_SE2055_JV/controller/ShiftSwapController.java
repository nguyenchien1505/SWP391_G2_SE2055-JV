package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.CreateShiftSwapRequest;
import com.example.SWP391_G2_SE2055_JV.dto.ShiftSwapRequestResponse;
import com.example.SWP391_G2_SE2055_JV.dto.SwapCandidateResponse;
import com.example.SWP391_G2_SE2055_JV.enums.SwapRequestStatus;
import com.example.SWP391_G2_SE2055_JV.service.ShiftSwapService;
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
 * Đổi ca — BR-SCH-09..12, 18, 19, 23, 25 (Milestone 2). Nhân viên gửi và trả lời yêu cầu; Manager
 * duyệt cho khách sạn của mình. Ai được làm gì trên TỪNG yêu cầu (đúng người gửi, đúng người nhận,
 * đúng khách sạn) do {@link ShiftSwapService} kiểm.
 */
@RestController
@RequestMapping("/scheduling/shift-swap-requests")
@RequiredArgsConstructor
public class ShiftSwapController {

    private final ShiftSwapService shiftSwapService;

    /** Ca của đồng nghiệp đổi được với ca {@code shiftId} của người đang đăng nhập. */
    @GetMapping("/candidates")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<List<SwapCandidateResponse>> getCandidates(@RequestParam UUID shiftId) {
        return ResponseEntity.ok(shiftSwapService.getCandidates(shiftId));
    }

    /**
     * @param box      {@code SENT} (tôi gửi), {@code RECEIVED} (gửi cho tôi) hoặc {@code LOCATION}
     *                 (mọi yêu cầu của khách sạn — chỉ Manager); viết hoa đúng tên enum
     * @param statuses bỏ trống = mọi trạng thái
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF','MANAGER')")
    public ResponseEntity<Page<ShiftSwapRequestResponse>> list(
            @RequestParam ShiftSwapService.Box box,
            @RequestParam(name = "status", required = false) List<SwapRequestStatus> statuses,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(shiftSwapService.list(box, statuses, pageable));
    }

    @PostMapping
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<ShiftSwapRequestResponse> create(@Valid @RequestBody CreateShiftSwapRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shiftSwapService.create(request));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<ShiftSwapRequestResponse> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(shiftSwapService.cancel(id));
    }

    @PatchMapping("/{id}/accept")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<ShiftSwapRequestResponse> accept(@PathVariable UUID id) {
        return ResponseEntity.ok(shiftSwapService.accept(id));
    }

    @PatchMapping("/{id}/decline")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<ShiftSwapRequestResponse> decline(@PathVariable UUID id) {
        return ResponseEntity.ok(shiftSwapService.decline(id));
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ShiftSwapRequestResponse> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(shiftSwapService.approve(id));
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ShiftSwapRequestResponse> reject(@PathVariable UUID id) {
        return ResponseEntity.ok(shiftSwapService.reject(id));
    }
}
