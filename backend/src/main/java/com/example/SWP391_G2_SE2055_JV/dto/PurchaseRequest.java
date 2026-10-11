package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Mua gói lần đầu: số lượng Giám đốc chọn cho chu kỳ 30 ngày (BR-SAAS-02). */
@Data
public class PurchaseRequest {

    @NotNull(message = "Số khách sạn là bắt buộc")
    @Min(value = 1, message = "Cần ít nhất 1 khách sạn")
    @Max(value = 10000, message = "Số khách sạn quá lớn")
    private Integer quotaLocation;

    @NotNull(message = "Số nhân viên là bắt buộc")
    @Min(value = 0, message = "Số nhân viên không được âm")
    @Max(value = 100000, message = "Số nhân viên quá lớn")
    private Integer quotaUser;

    @NotNull(message = "Số phòng là bắt buộc")
    @Min(value = 0, message = "Số phòng không được âm")
    @Max(value = 100000, message = "Số phòng quá lớn")
    private Integer quotaRoom;
}
