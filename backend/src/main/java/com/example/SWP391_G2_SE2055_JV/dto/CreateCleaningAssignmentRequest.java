package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateCleaningAssignmentRequest {

    @NotNull(message = "Shift is required")
    private Long shiftId;

    @NotNull(message = "Room is required")
    private Long roomId;
}
