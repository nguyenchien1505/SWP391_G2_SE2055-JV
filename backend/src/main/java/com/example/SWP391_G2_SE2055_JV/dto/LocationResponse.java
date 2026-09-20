package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.enums.LocationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class LocationResponse {

    private UUID           id;
    private String         name;
    private String         address;
    private String         phone;
    private Integer        starRating;
    private String         timezone;
    private LocationStatus status;

    /** BR-ORG-04: derived field — đếm phòng đang hoạt động, không lưu cột riêng. */
    private long           totalRooms;

    private LocalDateTime  createdAt;
    private LocalDateTime  updatedAt;

    public static LocationResponse fromEntity(Location location, long totalRooms) {
        return LocationResponse.builder()
            .id(location.getId())
            .name(location.getName())
            .address(location.getAddress())
            .phone(location.getPhone())
            .starRating(location.getStarRating())
            .timezone(location.getTimezone())
            .status(location.getStatus())
            .totalRooms(totalRooms)
            .createdAt(location.getCreatedAt())
            .updatedAt(location.getUpdatedAt())
            .build();
    }
}
