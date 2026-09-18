package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.enums.ShiftStatus;
import com.example.SWP391_G2_SE2055_JV.enums.ShiftType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftResponse {

    private Long id;
    private Long userId;
    private ShiftType shiftType;
    private LocalDate shiftDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private ShiftStatus status;

    public static ShiftResponse fromEntity(Shift shift) {
        return ShiftResponse.builder()
            .id(shift.getId())
            .userId(shift.getUserId())
            .shiftType(shift.getShiftType())
            .shiftDate(shift.getShiftDate())
            .startTime(shift.getStartTime())
            .endTime(shift.getEndTime())
            .status(shift.getStatus())
            .build();
    }
}
