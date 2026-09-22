package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.entity.RoomType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class RoomTypeResponse {

    private UUID          id;
    private String        name;

    /** BR-ORG-14: false = đã ẩn khỏi danh sách chọn, không phải đã xóa. */
    private boolean       active;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static RoomTypeResponse fromEntity(RoomType roomType) {
        return RoomTypeResponse.builder()
            .id(roomType.getId())
            .name(roomType.getName())
            .active(roomType.isActive())
            .createdAt(roomType.getCreatedAt())
            .updatedAt(roomType.getUpdatedAt())
            .build();
    }
}
