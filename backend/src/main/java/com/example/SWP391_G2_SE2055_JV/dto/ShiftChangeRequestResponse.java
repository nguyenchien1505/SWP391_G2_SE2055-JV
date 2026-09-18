package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.entity.ShiftChangeRequest;
import com.example.SWP391_G2_SE2055_JV.enums.ShiftChangeRequestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftChangeRequestResponse {

    private Long id;
    private Long shiftId;
    private Long requestedBy;
    private LocalDate requestedDate;
    private LocalTime requestedStartTime;
    private LocalTime requestedEndTime;
    private String reason;
    private ShiftChangeRequestStatus status;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewNote;
    private LocalDateTime createdAt;

    public static ShiftChangeRequestResponse fromEntity(ShiftChangeRequest request) {
        return ShiftChangeRequestResponse.builder()
            .id(request.getId())
            .shiftId(request.getShiftId())
            .requestedBy(request.getRequestedBy())
            .requestedDate(request.getRequestedDate())
            .requestedStartTime(request.getRequestedStartTime())
            .requestedEndTime(request.getRequestedEndTime())
            .reason(request.getReason())
            .status(request.getStatus())
            .reviewedBy(request.getReviewedBy())
            .reviewedAt(request.getReviewedAt())
            .reviewNote(request.getReviewNote())
            .createdAt(request.getCreatedAt())
            .build();
    }
}
