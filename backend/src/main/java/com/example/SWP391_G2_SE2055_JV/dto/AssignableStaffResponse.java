package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.entity.User;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * Một ứng viên nhận task dọn phòng — dữ liệu cho hộp thoại phân công (S-10).
 *
 * <p>Chỉ 3 trường: màn hình chỉ cần đủ để Manager nhận ra người và gọi điện nếu cần. Không dùng
 * lại {@code UserResponse} vì DTO đó mang cả email, ngày sinh, địa chỉ — danh sách này hiện cho
 * người không quản lý nhân sự nên trả càng ít càng tốt.
 */
@Data
@Builder
public class AssignableStaffResponse {

    private UUID   id;
    private String fullName;
    private String phone;

    public static AssignableStaffResponse fromEntity(User staff) {
        return AssignableStaffResponse.builder()
            .id(staff.getId())
            .fullName(staff.getFullName())
            .phone(staff.getPhone())
            .build();
    }
}
