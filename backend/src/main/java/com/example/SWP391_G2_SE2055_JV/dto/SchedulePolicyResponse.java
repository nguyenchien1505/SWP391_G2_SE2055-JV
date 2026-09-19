package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.entity.SchedulePolicy;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class SchedulePolicyResponse {

    private UUID          id;
    private BigDecimal    maxHoursPerDay;
    private BigDecimal    maxHoursPerWeek;
    private int           maxConsecutiveShifts;
    private BigDecimal    minRestHoursBetweenShifts;
    private int           minDaysOffPerWeek;
    private int           swapResponseTimeoutHours;
    private LocalDateTime updatedAt;

    public static SchedulePolicyResponse fromEntity(SchedulePolicy policy) {
        return SchedulePolicyResponse.builder()
            .id(policy.getId())
            .maxHoursPerDay(policy.getMaxHoursPerDay())
            .maxHoursPerWeek(policy.getMaxHoursPerWeek())
            .maxConsecutiveShifts(policy.getMaxConsecutiveShifts())
            .minRestHoursBetweenShifts(policy.getMinRestHoursBetweenShifts())
            .minDaysOffPerWeek(policy.getMinDaysOffPerWeek())
            .swapResponseTimeoutHours(policy.getSwapResponseTimeoutHours())
            .updatedAt(policy.getUpdatedAt())
            .build();
    }
}
