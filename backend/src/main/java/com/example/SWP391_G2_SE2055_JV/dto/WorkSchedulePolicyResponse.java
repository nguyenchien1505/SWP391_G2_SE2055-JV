package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.entity.WorkSchedulePolicy;
import com.example.SWP391_G2_SE2055_JV.enums.PolicyStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkSchedulePolicyResponse {

    private Long id;
    private Long locationId;
    private String name;
    private Integer maxShiftsPerWeek;
    private Integer maxHoursPerWeek;
    private Integer minRestHours;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private PolicyStatus status;

    public static WorkSchedulePolicyResponse fromEntity(WorkSchedulePolicy policy) {
        return WorkSchedulePolicyResponse.builder()
            .id(policy.getId())
            .locationId(policy.getLocationId())
            .name(policy.getName())
            .maxShiftsPerWeek(policy.getMaxShiftsPerWeek())
            .maxHoursPerWeek(policy.getMaxHoursPerWeek())
            .minRestHours(policy.getMinRestHours())
            .effectiveFrom(policy.getEffectiveFrom())
            .effectiveTo(policy.getEffectiveTo())
            .status(policy.getStatus())
            .build();
    }
}
