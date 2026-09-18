package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.enums.PolicyStatus;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateWorkSchedulePolicyRequest {

    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    @Positive(message = "Max shifts per week must be positive")
    private Integer maxShiftsPerWeek;

    @Positive(message = "Max hours per week must be positive")
    private Integer maxHoursPerWeek;

    @Positive(message = "Min rest hours must be positive")
    private Integer minRestHours;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    private PolicyStatus status;
}
