package com.example.SWP391_G2_SE2055_JV.service;

/**
 * Phát ra khi một tài khoản vừa được cấp mật khẩu tạm — lúc tạo tài khoản (BR-USER-03)
 * hoặc khi Manager cấp lại mật khẩu.
 *
 * @param passwordReset {@code true} nếu là cấp lại mật khẩu, {@code false} nếu là tài khoản mới.
 */
public record AccountCredentialsIssuedEvent(String email,
                                            String fullName,
                                            String tempPassword,
                                            boolean passwordReset) {
}
