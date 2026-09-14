package com.example.SWP391_G2_SE2055_JV.employee.controller;

import com.example.SWP391_G2_SE2055_JV.employee.dto.CreateUserRequest;
import com.example.SWP391_G2_SE2055_JV.employee.dto.UpdateUserRequest;
import com.example.SWP391_G2_SE2055_JV.employee.dto.UserResponse;
import com.example.SWP391_G2_SE2055_JV.employee.service.UserService;
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

/**
 * REST Controller for User management (CRUD operations).
 *
 * Authorization:
 * - ADMIN_PLATFORM, MANAGER: full access (create, read, update, delete)
 * - DIRECTOR: read-only access
 * - Other roles: no access
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * GET /users
     * Get all users with pagination.
     * Access: ADMIN_PLATFORM, MANAGER, DIRECTOR
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_PLATFORM', 'MANAGER', 'DIRECTOR')")
    public ResponseEntity<Page<UserResponse>> getAllUsers(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(userService.getAllUsers(pageable));
    }

    /**
     * GET /users/{id}
     * Get a single user by ID.
     * Access: ADMIN_PLATFORM, MANAGER, DIRECTOR
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_PLATFORM', 'MANAGER', 'DIRECTOR')")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    /**
     * POST /users
     * Create a new user account.
     * Generates temporary password and sends email.
     * Access: ADMIN_PLATFORM, MANAGER only
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_PLATFORM', 'MANAGER')")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * PUT /users/{id}
     * Update an existing user (partial update).
     * Access: ADMIN_PLATFORM, MANAGER only
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_PLATFORM', 'MANAGER')")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    /**
     * DELETE /users/{id}
     * Delete a user by ID.
     * Access: ADMIN_PLATFORM, MANAGER only
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_PLATFORM', 'MANAGER')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /users/{id}/reset-password
     * Reset user password (admin action).
     * Generates new temporary password and sends email.
     * Access: ADMIN_PLATFORM, MANAGER only
     */
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAnyRole('ADMIN_PLATFORM', 'MANAGER')")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id) {
        userService.resetUserPassword(id);
        return ResponseEntity.ok().build();
    }
}
