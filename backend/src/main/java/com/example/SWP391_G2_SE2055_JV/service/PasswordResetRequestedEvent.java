package com.example.SWP391_G2_SE2055_JV.service;

/**
 * Phát ra khi một nhân viên báo quên mật khẩu — {@code AccountEmailService} chuyển lời nhắn tới Quản lý khách
 * sạn của họ. Không đổi gì trên tài khoản: Quản lý tự quyết cấp mật khẩu tạm ở màn «Nhân viên chi nhánh».
 *
 * @param locationName tên khách sạn của nhân viên; {@code null} nếu không tra được
 */
public record PasswordResetRequestedEvent(String managerEmail,
                                          String managerName,
                                          String staffName,
                                          String staffEmail,
                                          String locationName) {
}
