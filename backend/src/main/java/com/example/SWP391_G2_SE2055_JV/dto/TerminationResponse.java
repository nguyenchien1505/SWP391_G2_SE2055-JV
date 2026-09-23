package com.example.SWP391_G2_SE2055_JV.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Kết quả cho nghỉ việc.
 *
 * <p>{@code replacement} là Manager nhận khách sạn (null nếu không có bàn giao).
 * {@code tempPassword} chỉ có khi Manager thay thế vừa được TẠO MỚI — hiển thị đúng một lần
 * như {@link TempPasswordResponse} (BR-USER-07).
 */
@Data
@AllArgsConstructor
public class TerminationResponse {

    private UserResponse user;
    private UserResponse replacement;
    private String       tempPassword;
}
