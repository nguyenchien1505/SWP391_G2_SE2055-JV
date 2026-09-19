package com.example.SWP391_G2_SE2055_JV.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Mật khẩu tạm trả về ĐÚNG MỘT LẦN ngay trong response — BR-USER-03, BR-USER-07.
 *
 * <p>Milestone 1 KHÔNG tích hợp gửi email/SMS (BR-OUT-01): màn hình hiển thị mật khẩu
 * này cho Manager, Manager tự thông báo thủ công cho nhân viên. Hệ thống chỉ lưu hash
 * nên giá trị này không xem lại được.
 */
@Data
@AllArgsConstructor
public class TempPasswordResponse {

    private UserResponse user;
    private String       tempPassword;
}
