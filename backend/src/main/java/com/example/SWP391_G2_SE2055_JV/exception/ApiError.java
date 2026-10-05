package com.example.SWP391_G2_SE2055_JV.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {
    private final LocalDateTime timestamp;
    private final int status;
    private final String error;
    private final String message;
    private final String path;
    private final List<FieldError> fieldErrors;

    /** Lý do từng nhân viên bị chặn khi giao một ca cho nhiều người — xem {@link ShiftBatchRejectedException}. */
    private final List<StaffViolation> violations;

    @Getter
    @Builder
    public static class FieldError {
        private final String field;
        private final String message;
    }

    @Getter
    @Builder
    public static class StaffViolation {
        private final UUID   staffId;
        private final String fullName;
        private final String message;
    }
}