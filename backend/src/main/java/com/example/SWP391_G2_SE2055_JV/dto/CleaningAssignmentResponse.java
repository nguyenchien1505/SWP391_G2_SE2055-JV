package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.entity.CleaningAssignment;
import com.example.SWP391_G2_SE2055_JV.enums.CleaningAssignmentStatus;
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
