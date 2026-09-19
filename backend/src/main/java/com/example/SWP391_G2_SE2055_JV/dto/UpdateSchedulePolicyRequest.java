package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Sửa bộ quy tắc xếp ca của Tenant — BR-SCH-01, BR-SCH-02, BR-SCH-21.
 *
 * <p>DM-18: sửa đè trực tiếp, không lưu lịch sử phiên bản, nên đây là PUT toàn phần
 * chứ không phải PATCH từng trường.
 */
@Data
public class UpdateSchedulePolicyRequest {

    @NotNull
    @DecimalMin(value = "0.5", message = "Giờ làm tối đa/ngày phải lớn hơn 0")
    private BigDecimal maxHoursPerDay;

    @NotNull
    @DecimalMin(value = "0.5", message = "Giờ làm tối đa/tuần phải lớn hơn 0")
    private BigDecimal maxHoursPerWeek;

    /** Đếm theo SỐ NGÀY liên tiếp có ca — BR-SCH-14. */
    @NotNull
    @Min(value = 1, message = "Số ngày làm liên tiếp tối đa phải từ 1 trở lên")
    private Integer maxConsecutiveShifts;

    @NotNull
    @DecimalMin(value = "0.0", message = "Giờ nghỉ tối thiểu giữa 2 ca không được âm")
    private BigDecimal minRestHoursBetweenShifts;

    @NotNull
    @Min(value = 0, message = "Số ngày nghỉ tối thiểu/tuần phải từ 0 đến 7")
    @Max(value = 7, message = "Số ngày nghỉ tối thiểu/tuần phải từ 0 đến 7")
    private Integer minDaysOffPerWeek;

    /** Hạn phản hồi yêu cầu đổi ca, cấp Tenant — BR-SCH-11, BR-SCH-21. */
    @NotNull
    @Min(value = 1, message = "Thời gian chờ phản hồi đổi ca phải từ 1 giờ trở lên")
    private Integer swapResponseTimeoutHours;
}
