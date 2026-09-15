package com.example.SWP391_G2_SE2055_JV.housekeeping.dto;

import com.example.SWP391_G2_SE2055_JV.config.CleaningAssignmentStatus;
import com.example.SWP391_G2_SE2055_JV.housekeeping.entity.CleaningAssignment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CleaningAssignmentResponse {

    private Long id;
    private Long shiftId;
    private Long roomId;
    private CleaningAssignmentStatus status;
    private LocalDateTime assignedAt;
    private LocalDateTime completedAt;

    public static CleaningAssignmentResponse fromEntity(CleaningAssignment assignment) {
        return CleaningAssignmentResponse.builder()
            .id(assignment.getId())
            .shiftId(assignment.getShiftId())
            .roomId(assignment.getRoomId())
            .status(assignment.getStatus())
            .assignedAt(assignment.getAssignedAt())
            .completedAt(assignment.getCompletedAt())
            .build();
    }
}
