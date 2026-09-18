package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.enums.CleaningAssignmentStatus;
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
