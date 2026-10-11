package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Body của {@code POST /auth/verify-email}: mã lấy từ link trong email. */
@Data
public class VerifyEmailRequest {

    @NotBlank(message = "Thiếu mã xác thực")
    private String token;
}
