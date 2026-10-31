package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.AttendanceIpModeRequest;
import com.example.SWP391_G2_SE2055_JV.dto.AttendanceNetworkRequest;
import com.example.SWP391_G2_SE2055_JV.dto.AttendanceNetworkSettingsResponse;
import com.example.SWP391_G2_SE2055_JV.dto.AttendanceRowResponse;
import com.example.SWP391_G2_SE2055_JV.dto.AttendanceSummaryResponse;
import com.example.SWP391_G2_SE2055_JV.dto.ClientInfo;
import com.example.SWP391_G2_SE2055_JV.dto.TodayAttendanceResponse;
import com.example.SWP391_G2_SE2055_JV.service.AttendanceNetworkService;
import com.example.SWP391_G2_SE2055_JV.service.AttendanceService;
import com.example.SWP391_G2_SE2055_JV.service.AttendanceSummaryService;
import com.example.SWP391_G2_SE2055_JV.service.AttendanceTodayService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Bảng chấm công và mạng chấm công của một khách sạn (chấm công M2). Nhân viên xem chấm công của mình qua lịch
 * cá nhân ({@code GET /scheduling/shifts}); vào / ra ca và Manager sửa giờ nằm ở {@link ShiftController}.
 */
@RestController
@RequestMapping("/scheduling/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService        attendanceService;
    private final AttendanceNetworkService networkService;
    private final AttendanceTodayService   todayService;
    private final AttendanceSummaryService summaryService;

    /**
     * @param locationId Giám đốc / Admin chọn khách sạn; Manager bỏ qua — luôn là khách sạn của mình
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER')")
    public ResponseEntity<List<AttendanceRowResponse>> getAttendance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID locationId) {
        return ResponseEntity.ok(attendanceService.getAttendance(from, to, locationId));
    }

    /**
     * Khối «Hôm nay»: ca của ngày làm hiện tại, ca đang không có lễ tân trong ca, thao tác của lễ tân lúc chưa
     * vào ca.
     */
    @GetMapping("/today")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER')")
    public ResponseEntity<TodayAttendanceResponse> getToday(@RequestParam(required = false) UUID locationId) {
        return ResponseEntity.ok(todayService.getToday(locationId));
    }

    // ── Tổng kết chuyên cần tháng — chỉ để xem, không tính lương ──────────────

    /** @param month "YYYY-MM", bỏ trống là tháng này. Mỗi nhân viên có ca ở khách sạn trong tháng một dòng. */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER')")
    public ResponseEntity<List<AttendanceSummaryResponse>> getSummary(
            @RequestParam(required = false) String month, @RequestParam(required = false) UUID locationId) {
        return ResponseEntity.ok(summaryService.getLocationSummary(month, locationId));
    }

    /** Nhân viên xem chuyên cần tháng của chính mình. */
    @GetMapping("/summary/me")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<AttendanceSummaryResponse> getMySummary(@RequestParam(required = false) String month) {
        return ResponseEntity.ok(summaryService.getMySummary(month));
    }

    // ── Mạng chấm công (kiểm IP) — Manager sửa, Giám đốc xem ─────────────────

    /** Kèm IP của chính người đang xem — để bấm «Thêm IP mạng hiện tại». */
    @GetMapping("/network")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER')")
    public ResponseEntity<AttendanceNetworkSettingsResponse> getNetwork(
            @RequestParam(required = false) UUID locationId, HttpServletRequest http) {
        return ResponseEntity.ok(networkService.getSettings(locationId, ClientInfo.from(http)));
    }

    @PutMapping("/network/mode")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','MANAGER')")
    public ResponseEntity<AttendanceNetworkSettingsResponse> setNetworkMode(
            @RequestParam(required = false) UUID locationId,
            @Valid @RequestBody AttendanceIpModeRequest request, HttpServletRequest http) {
        return ResponseEntity.ok(networkService.setMode(locationId, request.getMode(), ClientInfo.from(http)));
    }

    @PostMapping("/network/ips")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','MANAGER')")
    public ResponseEntity<AttendanceNetworkSettingsResponse> addNetwork(
            @RequestParam(required = false) UUID locationId,
            @Valid @RequestBody AttendanceNetworkRequest request, HttpServletRequest http) {
        return ResponseEntity.ok(networkService.addNetwork(locationId, request, ClientInfo.from(http)));
    }

    @DeleteMapping("/network/ips/{networkId}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','MANAGER')")
    public ResponseEntity<AttendanceNetworkSettingsResponse> removeNetwork(
            @RequestParam(required = false) UUID locationId,
            @PathVariable UUID networkId, HttpServletRequest http) {
        return ResponseEntity.ok(networkService.removeNetwork(locationId, networkId, ClientInfo.from(http)));
    }
}
