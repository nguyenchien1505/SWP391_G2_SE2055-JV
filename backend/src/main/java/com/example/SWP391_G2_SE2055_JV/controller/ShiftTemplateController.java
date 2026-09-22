package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.CreateShiftTemplateRequest;
import com.example.SWP391_G2_SE2055_JV.dto.SetActiveRequest;
import com.example.SWP391_G2_SE2055_JV.dto.ShiftTemplateResponse;
import com.example.SWP391_G2_SE2055_JV.dto.UpdateShiftTemplateRequest;
import com.example.SWP391_G2_SE2055_JV.service.ShiftTemplateService;
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
 * Mẫu ca — Giám đốc định nghĩa ở cấp Tenant (BR-SCH-04), Manager chỉ đọc để chọn khi xếp ca.
 *
 * <p>Không có {@code DELETE}: BR-SCH-22 quy định chỉ vô hiệu hóa, dùng
 * {@code PATCH /{id}/active}.
 */
@RestController
@RequestMapping("/scheduling/shift-templates")
@RequiredArgsConstructor
public class ShiftTemplateController {

    private final ShiftTemplateService shiftTemplateService;

    /**
     * @param includeInactive true để quản trị danh mục (thấy cả mẫu đã tắt); mặc định false
     *                        cho màn hình xếp ca — BR-SCH-22.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER')")
    public ResponseEntity<Page<ShiftTemplateResponse>> getTemplates(
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @PageableDefault(size = 20, sort = "startTime", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(shiftTemplateService.getTemplates(includeInactive, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER')")
    public ResponseEntity<ShiftTemplateResponse> getTemplateById(@PathVariable UUID id) {
        return ResponseEntity.ok(shiftTemplateService.getTemplateById(id));
    }

    /** BR-SCH-04: mẫu ca do Giám đốc định nghĩa sẵn ở cấp Tenant. */
    @PostMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR')")
    public ResponseEntity<ShiftTemplateResponse> createTemplate(
            @Valid @RequestBody CreateShiftTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(shiftTemplateService.createTemplate(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR')")
    public ResponseEntity<ShiftTemplateResponse> updateTemplate(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateShiftTemplateRequest request) {
        return ResponseEntity.ok(shiftTemplateService.updateTemplate(id, request));
    }

    /** BR-SCH-22: bật/tắt thay cho xóa cứng. */
    @PatchMapping("/{id}/active")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR')")
    public ResponseEntity<ShiftTemplateResponse> setActive(
            @PathVariable UUID id,
            @Valid @RequestBody SetActiveRequest request) {
        return ResponseEntity.ok(shiftTemplateService.setActive(id, request.getActive()));
    }
}
