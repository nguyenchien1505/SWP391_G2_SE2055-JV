package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.DepartmentRequest;
import com.example.SWP391_G2_SE2055_JV.dto.DepartmentResponse;
import com.example.SWP391_G2_SE2055_JV.dto.SetActiveRequest;
import com.example.SWP391_G2_SE2055_JV.service.DepartmentService;
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
 * Phòng ban — danh mục cấp Tenant, chỉ Giám đốc tạo (BR-ORG-06).
 *
 * <p>Manager chỉ đọc: họ cần danh sách để chọn khi tạo nhân sự, không được tự thêm mục mới.
 */
@RestController
@RequestMapping("/organization/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    /**
     * @param includeInactive true để quản trị danh mục (thấy cả mục đã ẩn); mặc định false
     *                        cho danh sách chọn — BR-ORG-14.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER')")
    public ResponseEntity<Page<DepartmentResponse>> getDepartments(
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(departmentService.getDepartments(includeInactive, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER')")
    public ResponseEntity<DepartmentResponse> getDepartmentById(@PathVariable UUID id) {
        return ResponseEntity.ok(departmentService.getDepartmentById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR')")
    public ResponseEntity<DepartmentResponse> createDepartment(
            @Valid @RequestBody DepartmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(departmentService.createDepartment(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR')")
    public ResponseEntity<DepartmentResponse> updateDepartment(
            @PathVariable UUID id,
            @Valid @RequestBody DepartmentRequest request) {
        return ResponseEntity.ok(departmentService.updateDepartment(id, request));
    }

    /** BR-ORG-14: ẩn/hiện thay cho xóa, vì BR-ORG-10 chặn cứng xóa mục đang dùng. */
    @PatchMapping("/{id}/active")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR')")
    public ResponseEntity<DepartmentResponse> setActive(
            @PathVariable UUID id,
            @Valid @RequestBody SetActiveRequest request) {
        return ResponseEntity.ok(departmentService.setActive(id, request.getActive()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR')")
    public ResponseEntity<Void> deleteDepartment(@PathVariable UUID id) {
        departmentService.deleteDepartment(id);
        return ResponseEntity.noContent().build();
    }
}
