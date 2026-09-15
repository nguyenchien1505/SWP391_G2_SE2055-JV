package com.example.SWP391_G2_SE2055_JV.scheduling.dto;

import com.example.SWP391_G2_SE2055_JV.config.ShiftStatus;
import com.example.SWP391_G2_SE2055_JV.config.ShiftType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateShiftRequest {

    private ShiftType shiftType;
    private LocalDate shiftDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private ShiftStatus status;
}
