package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Sửa cấu hình cấp hệ thống — BR-SAAS-08, BR-SAAS-10.
 *
 * <p>PUT toàn phần: bảng chỉ có một bản ghi và cả hai giá trị đều bắt buộc gửi lên.
 *
 * <p>Dùng {@code Integer} (kiểu bọc) thay vì {@code int} để {@code @NotNull} phát hiện được
 * trường bị thiếu. Với {@code int}, trường thiếu sẽ âm thầm thành 0 và lọt qua kiểm tra.
 *
 * <p>Giới hạn trên (365 / 90 ngày) là chốt chặn chống nhập nhầm do team đề xuất; DB chỉ
 * CHECK {@code trial_days > 0} và {@code grace_period_days >= 0}.
 */
@Data
public class UpdateSystemConfigRequest {

    /** Số ngày dùng thử cấp cho Tenant MỚI — BR-SAAS-08. Không ảnh hưởng Tenant đang dùng thử. */
    @NotNull(message = "Số ngày dùng thử là bắt buộc")
    @Min(value = 1, message = "Số ngày dùng thử phải từ 1 trở lên")
    @Max(value = 365, message = "Số ngày dùng thử tối đa 365")
    private Integer trialDays;

    /** Số ngày Payment overdue trước khi khóa Tenant — BR-SAAS-10. */
    @NotNull(message = "Số ngày ân hạn là bắt buộc")
    @Min(value = 0, message = "Số ngày ân hạn không được âm")
    @Max(value = 90, message = "Số ngày ân hạn tối đa 90")
    private Integer gracePeriodDays;
}
