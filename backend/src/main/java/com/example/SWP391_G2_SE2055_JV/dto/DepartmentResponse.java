package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.entity.Department;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class DepartmentResponse {

    private UUID          id;
    private String        name;

    /** BR-ORG-14: false = đã ẩn khỏi danh sách chọn, không phải đã xóa. */
    private boolean       active;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static DepartmentResponse fromEntity(Department department) {
        return DepartmentResponse.builder()
            .id(department.getId())
            .name(department.getName())
            .active(department.isActive())
            .createdAt(department.getCreatedAt())
            .updatedAt(department.getUpdatedAt())
            .build();
    }
}
