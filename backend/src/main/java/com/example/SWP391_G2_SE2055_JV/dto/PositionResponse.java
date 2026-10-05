package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.entity.Department;
import com.example.SWP391_G2_SE2055_JV.entity.Position;
import com.example.SWP391_G2_SE2055_JV.enums.PositionType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class PositionResponse {

    private UUID          id;
    private String        name;
    private PositionType  positionType;
    private UUID          departmentId;

    /** BR-ORG-07: Department suy ra từ Position, kèm sẵn tên để màn hình khỏi gọi thêm. */
    private String        departmentName;

    /**
     * Phòng ban đang hiện hay đã ẩn. Chức danh chỉ CHỌN được khi cả nó lẫn phòng ban đều đang
     * hiện (BR-ORG-14) — màn hình dựa vào đây để lọc ô chọn.
     */
    private boolean       departmentActive;

    private boolean       active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PositionResponse fromEntity(Position position, Department department) {
        return PositionResponse.builder()
            .id(position.getId())
            .name(position.getName())
            .positionType(position.getPositionType())
            .departmentId(position.getDepartmentId())
            .departmentName(department != null ? department.getName() : null)
            .departmentActive(department != null && department.isActive())
            .active(position.isActive())
            .createdAt(position.getCreatedAt())
            .updatedAt(position.getUpdatedAt())
            .build();
    }
}
