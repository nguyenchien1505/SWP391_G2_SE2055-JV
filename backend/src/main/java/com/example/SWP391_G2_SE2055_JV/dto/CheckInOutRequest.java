package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.Valid;
import lombok.Data;

/**
 * Body (không bắt buộc) của {@code POST /scheduling/shifts/{id}/check-in} và {@code …/check-out}. Giao diện
 * cũ không gửi body vẫn chạy như không xác thực.
 */
@Data
public class CheckInOutRequest {

    /** Để trống ở M2. */
    @Valid
    private AttendanceVerification verification;
}
