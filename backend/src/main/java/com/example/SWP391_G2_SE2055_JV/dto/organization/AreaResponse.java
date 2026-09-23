package com.example.SWP391_G2_SE2055_JV.dto.organization;

import com.example.SWP391_G2_SE2055_JV.entity.Area;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AreaResponse {
    private UUID id;
    private UUID locationId;
    private String name;

    public static AreaResponse fromEntity(Area area) {
        if (area == null) return null;
        return AreaResponse.builder()
                .id(area.getId())
                .locationId(area.getLocationId())
                .name(area.getName())
                .build();
    }
}
