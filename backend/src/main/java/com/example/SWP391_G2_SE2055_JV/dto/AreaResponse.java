package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.entity.Area;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class AreaResponse {

    private UUID          id;
    private UUID          locationId;
    private String        name;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AreaResponse fromEntity(Area area) {
        return AreaResponse.builder()
            .id(area.getId())
            .locationId(area.getLocationId())
            .name(area.getName())
            .createdAt(area.getCreatedAt())
            .updatedAt(area.getUpdatedAt())
            .build();
    }
}
