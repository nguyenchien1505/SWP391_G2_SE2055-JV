package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.enums.ShiftType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateShiftRequest {

    @NotNull(message = "User is required")
    private Long userId;

    private ShiftType shiftType;

    @NotNull(message = "Shift date is required")
    private LocalDate shiftDate;

    private LocalTime startTime;

    private LocalTime endTime;
}
