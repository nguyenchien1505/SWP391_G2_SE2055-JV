package com.example.SWP391_G2_SE2055_JV.scheduling.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateShiftChangeRequest {

    @NotNull(message = "Shift is required")
    private Long shiftId;

    private LocalDate requestedDate;

    private LocalTime requestedStartTime;

    private LocalTime requestedEndTime;

    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;
}
