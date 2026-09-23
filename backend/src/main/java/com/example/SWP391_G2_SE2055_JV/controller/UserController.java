package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.CreateUserRequest;
import com.example.SWP391_G2_SE2055_JV.dto.TempPasswordResponse;
import com.example.SWP391_G2_SE2055_JV.dto.UpdateUserRequest;
import com.example.SWP391_G2_SE2055_JV.dto.UserResponse;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.service.UserService;
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
 * Quản lý nhân sự — BR-PERM-02 (Giám đốc CRUD Manager, View Staff) và BR-PERM-03
 * (Manager CRUD Staff trong Location của mình).
 *
 * <p>Phạm vi dữ liệu theo Tenant và Location được ép ở {@code UserService}, không
 * phụ thuộc vào rule URL.
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER')")
    public ResponseEntity<Page<UserResponse>> getUsers(
            @RequestParam(required = false) Role role,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(userService.getUsers(role, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER')")
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    /**
     * Tạo hồ sơ + tài khoản trong 1 bước. Response chứa mật khẩu tạm hiển thị ĐÚNG
     * MỘT LẦN cho Manager tự thông báo thủ công — BR-USER-03 (không gửi email/SMS).
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER')")
    public ResponseEntity<TempPasswordResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER')")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    /**
     * Cho nghỉ việc — BR-USER-04. Là XÓA MỀM: tài khoản chuyển "Đã nghỉ việc", dữ liệu
     * lịch sử giữ nguyên, ca tương lai tự gỡ thành chưa phân công. Không có xóa cứng.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER')")
    public ResponseEntity<UserResponse> terminateUser(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.terminateUser(id));
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER')")
    public ResponseEntity<TempPasswordResponse> resetPassword(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.resetUserPassword(id));
    }
}
