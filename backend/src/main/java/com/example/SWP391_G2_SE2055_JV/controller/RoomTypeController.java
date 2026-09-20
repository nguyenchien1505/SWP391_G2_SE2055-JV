package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.RoomTypeRequest;
import com.example.SWP391_G2_SE2055_JV.dto.RoomTypeResponse;
import com.example.SWP391_G2_SE2055_JV.dto.SetActiveRequest;
import com.example.SWP391_G2_SE2055_JV.service.RoomTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Loại phòng — danh mục cấp Tenant, Giám đốc định nghĩa, dùng chung mọi Location
 * (BR-ORG-11).
 */
@RestController
@RequestMapping("/organization/room-types")
@RequiredArgsConstructor
public class RoomTypeController {

    private final RoomTypeService roomTypeService;

    @GetMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER')")
    public ResponseEntity<List<RoomTypeResponse>> getRoomTypes(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(roomTypeService.getRoomTypes(includeInactive));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR','MANAGER')")
    public ResponseEntity<RoomTypeResponse> getRoomTypeById(@PathVariable UUID id) {
        return ResponseEntity.ok(roomTypeService.getRoomTypeById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR')")
    public ResponseEntity<RoomTypeResponse> createRoomType(@Valid @RequestBody RoomTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roomTypeService.createRoomType(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR')")
    public ResponseEntity<RoomTypeResponse> updateRoomType(
            @PathVariable UUID id,
            @Valid @RequestBody RoomTypeRequest request) {
        return ResponseEntity.ok(roomTypeService.updateRoomType(id, request));
    }

    /** BR-ORG-14: ẩn/hiện thay cho xóa. */
    @PatchMapping("/{id}/active")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR')")
    public ResponseEntity<RoomTypeResponse> setActive(
            @PathVariable UUID id,
            @Valid @RequestBody SetActiveRequest request) {
        return ResponseEntity.ok(roomTypeService.setActive(id, request.getActive()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DIRECTOR')")
    public ResponseEntity<Void> deleteRoomType(@PathVariable UUID id) {
        roomTypeService.deleteRoomType(id);
        return ResponseEntity.noContent().build();
    }
}
