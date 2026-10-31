package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.entity.AttendanceAdjustmentRequest;
import com.example.SWP391_G2_SE2055_JV.enums.AdjustmentRequestStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/** Một đơn điều chỉnh công kèm ca (giờ đang có) và tên người gửi / người duyệt — màn hình không phải tra thêm. */
@Data
@Builder
public class AttendanceAdjustmentResponse {

    private UUID                    id;
    private ShiftResponse           shift;
    private UUID                    requesterId;
    private String                  requesterName;
    private LocalDateTime           requestedCheckInAt;
    private LocalDateTime           requestedCheckOutAt;
    private String                  reason;
    private AdjustmentRequestStatus status;
    private String                  reviewerName;
    private LocalDateTime           decidedAt;
    private String                  reviewNote;
    private LocalDateTime           createdAt;

    public static AttendanceAdjustmentResponse fromEntity(AttendanceAdjustmentRequest request, ShiftResponse shift,
                                                          String requesterName, String reviewerName) {
        return AttendanceAdjustmentResponse.builder()
            .id(request.getId())
            .shift(shift)
            .requesterId(request.getRequesterId())
            .requesterName(requesterName)
            .requestedCheckInAt(request.getRequestedCheckInAt())
            .requestedCheckOutAt(request.getRequestedCheckOutAt())
            .reason(request.getReason())
            .status(request.getStatus())
            .reviewerName(reviewerName)
            .decidedAt(request.getDecidedAt())
            .reviewNote(request.getReviewNote())
            .createdAt(request.getCreatedAt())
            .build();
    }
}
