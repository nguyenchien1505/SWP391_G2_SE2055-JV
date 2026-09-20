package com.example.SWP391_G2_SE2055_JV.dto;

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

    private boolean       active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PositionResponse fromEntity(Position position, String departmentName) {
        return PositionResponse.builder()
            .id(position.getId())
            .name(position.getName())
            .positionType(position.getPositionType())
            .departmentId(position.getDepartmentId())
            .departmentName(departmentName)
            .active(position.isActive())
            .createdAt(position.getCreatedAt())
            .updatedAt(position.getUpdatedAt())
            .build();
    }
}
