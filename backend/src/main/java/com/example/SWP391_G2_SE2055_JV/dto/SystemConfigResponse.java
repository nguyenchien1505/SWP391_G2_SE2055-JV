package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.entity.SystemConfig;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Cấu hình hệ thống trả về client. Không lộ id vì bảng chỉ có một bản ghi.
 *
 * <p>{@code updatedAt} là NULL nếu cấu hình chưa từng được sửa từ lúc seed.
 */
@Data
@Builder
public class SystemConfigResponse {

    private int           trialDays;
    private int           gracePeriodDays;
    private LocalDateTime updatedAt;

    public static SystemConfigResponse fromEntity(SystemConfig config) {
        return SystemConfigResponse.builder()
            .trialDays(config.getTrialDays())
            .gracePeriodDays(config.getGracePeriodDays())
            .updatedAt(config.getUpdatedAt())
            .build();
    }
}
