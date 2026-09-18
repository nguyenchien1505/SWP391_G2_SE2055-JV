package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.CreateShiftRequest;
import com.example.SWP391_G2_SE2055_JV.dto.ShiftResponse;
import com.example.SWP391_G2_SE2055_JV.dto.UpdateShiftRequest;
import com.example.SWP391_G2_SE2055_JV.service.ShiftService;
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
 * Work Schedule Management — creating/assigning work shifts and viewing the personal
 * work schedule. {@code GET} returns "my shifts" for RECEPTIONIST/HOUSEKEEPING and
 * "all shifts" for ADMIN_PLATFORM/DIRECTOR/MANAGER (see ShiftService#getShifts).
 *
 * Authorization:
 * - ADMIN_PLATFORM, DIRECTOR, MANAGER, RECEPTIONIST, HOUSEKEEPING: read (own or all)
 * - ADMIN_PLATFORM, MANAGER: create, update, delete
 */
@RestController
@RequestMapping("/scheduling/shifts")
@RequiredArgsConstructor
public class ShiftController {

    private final ShiftService shiftService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_PLATFORM', 'DIRECTOR', 'MANAGER', 'RECEPTIONIST', 'HOUSEKEEPING')")
    public ResponseEntity<Page<ShiftResponse>> getShifts(
            @PageableDefault(size = 20, sort = "shiftDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(shiftService.getShifts(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_PLATFORM', 'DIRECTOR', 'MANAGER', 'RECEPTIONIST', 'HOUSEKEEPING')")
    public ResponseEntity<ShiftResponse> getShiftById(@PathVariable Long id) {
        return ResponseEntity.ok(shiftService.getShiftById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_PLATFORM', 'MANAGER')")
    public ResponseEntity<ShiftResponse> createShift(@Valid @RequestBody CreateShiftRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shiftService.createShift(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_PLATFORM', 'MANAGER')")
    public ResponseEntity<ShiftResponse> updateShift(
            @PathVariable Long id,
            @Valid @RequestBody UpdateShiftRequest request) {
        return ResponseEntity.ok(shiftService.updateShift(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_PLATFORM', 'MANAGER')")
    public ResponseEntity<Void> deleteShift(@PathVariable Long id) {
        shiftService.deleteShift(id);
        return ResponseEntity.noContent().build();
    }
}
