package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Body của {@code POST /auth/forgot-password}: email đăng nhập của nhân viên quên mật khẩu. */
@Data
public class ForgotPasswordRequest {

    @NotBlank(message = "Email là bắt buộc")
    @Email(message = "Email không đúng định dạng")
    private String email;
}
