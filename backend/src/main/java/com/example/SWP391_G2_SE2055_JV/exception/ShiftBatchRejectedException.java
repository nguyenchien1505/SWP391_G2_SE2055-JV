package com.example.SWP391_G2_SE2055_JV.exception;

import lombok.Getter;

import java.util.List;

/**
 * Giao một ca cho nhiều người mà có người vi phạm quy định xếp ca (BR-SCH-02): KHÔNG lưu ca nào,
 * trả về lý do của TỪNG người để Manager bỏ chọn những người đó rồi lưu lại.
 *
 * <p>Là một {@link BusinessException} (400) nhưng có handler riêng để kèm danh sách {@code violations}.
 */
@Getter
public class ShiftBatchRejectedException extends BusinessException {

    private final transient List<ApiError.StaffViolation> violations;

    public ShiftBatchRejectedException(List<ApiError.StaffViolation> violations, int requested) {
        super(String.format(
            "Chưa lưu ca nào: %d/%d nhân viên vi phạm quy định xếp ca. Bỏ chọn những người này rồi lưu lại.",
            violations.size(), requested));
        this.violations = List.copyOf(violations);
    }
}
