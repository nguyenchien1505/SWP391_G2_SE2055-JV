package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Thông tin vận hành của Location — phần Manager được sửa (BR-ORG-03).
 *
 * <p>Giới hạn ở địa chỉ và số điện thoại liên hệ. Tên khách sạn, hạng sao và múi giờ
 * nằm ngoài phạm vi này: chúng thuộc quyền Giám đốc, riêng múi giờ còn làm dịch mốc
 * "ca tương lai" của toàn bộ lịch làm việc (BR-SCH-17).
 *
 * <p>Trường bỏ trống nghĩa là giữ nguyên.
 */
@Data
public class UpdateLocationContactRequest {

    @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự")
    private String address;

    @Size(max = 30, message = "Số điện thoại tối đa 30 ký tự")
    private String phone;
}
