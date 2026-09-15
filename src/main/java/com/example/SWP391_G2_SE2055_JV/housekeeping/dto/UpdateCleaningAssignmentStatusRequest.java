package com.example.SWP391_G2_SE2055_JV.housekeeping.dto;

import com.example.SWP391_G2_SE2055_JV.config.CleaningAssignmentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCleaningAssignmentStatusRequest {

    @NotNull(message = "Status is required")
    private CleaningAssignmentStatus status;
}
