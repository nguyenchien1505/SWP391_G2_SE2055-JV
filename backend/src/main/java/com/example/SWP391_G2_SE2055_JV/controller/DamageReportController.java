package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.asset.CreateDamageReportRequest;
import com.example.SWP391_G2_SE2055_JV.dto.asset.DamageReportResponse;
import com.example.SWP391_G2_SE2055_JV.enums.DamageReportStatus;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.service.DamageReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
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
 * Báo hỏng tài sản — BR-ASSET-05, BR-ASSET-06, BR-ASSET-11, DM-12, DM-16.
 *
 * <p>{@code SecurityConfig} đã có sẵn rule cho {@code POST /assets/damage-reports}
 * (Lễ tân/Dọn dẹp qua {@code POSITION_RECEPTION}/{@code POSITION_HOUSEKEEPING}) và
 * cho {@code GET /assets/**} (mở tới STAFF — phạm vi lọc theo người báo cáo nằm ở
 * service). Riêng {@code PATCH .../resolve} rơi vào catch-all
 * {@code /assets/**} (ADMIN, DIRECTOR, MANAGER) nên phải chặn thêm bằng
 * {@code @PreAuthorize} để loại Giám đốc — chỉ Manager xử lý được (BR-ASSET-06).
 */
@RestController
@RequestMapping("/assets/damage-reports")
@RequiredArgsConstructor
public class DamageReportController {

    private final DamageReportService damageReportService;

    /** BR-ASSET-05 — quyền đã chặn ở SecurityConfig theo POSITION, không lặp lại ở đây. */
    @PostMapping
    public ResponseEntity<DamageReportResponse> createDamageReport(
            @Valid @RequestBody CreateDamageReportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(damageReportService.createDamageReport(request));
    }

    /**
     * DM-16: KHÔNG truyền {@code status} thì mặc định lọc {@code NEW} — "màn hình tự
     * truy vấn danh sách đang chờ xử lý". Truyền {@code status=} rỗng để bỏ lọc, xem
     * cả báo cáo {@code RESOLVED}. Nhận {@code String} thay vì bind thẳng vào enum vì
     * Spring không phân biệt được "không truyền" (nên mặc định NEW) với "truyền chuỗi
     * rỗng" (nên bỏ lọc) một khi đã gắn {@code defaultValue} cho kiểu enum.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('DIRECTOR','MANAGER','STAFF')")
    public ResponseEntity<Page<DamageReportResponse>> getDamageReports(
            @RequestParam(required = false, defaultValue = "NEW") String status,
            @PageableDefault(size = 20, sort = "reportedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(damageReportService.getDamageReports(parseStatus(status), pageable));
    }

    private DamageReportStatus parseStatus(String status) {
        if (StringUtils.isBlank(status)) {
            return null;
        }
        try {
            return DamageReportStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Trạng thái báo hỏng không hợp lệ: " + status
                + ". Chỉ chấp nhận NEW hoặc RESOLVED.");
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DIRECTOR','MANAGER','STAFF')")
    public ResponseEntity<DamageReportResponse> getDamageReportById(@PathVariable UUID id) {
        return ResponseEntity.ok(damageReportService.getDamageReportById(id));
    }

    /** Chỉ Manager — BR-ASSET-06. Không đụng tới trạng thái tài sản/phòng. */
    @PatchMapping("/{id}/resolve")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<DamageReportResponse> resolveDamageReport(@PathVariable UUID id) {
        return ResponseEntity.ok(damageReportService.resolveDamageReport(id));
    }
}
