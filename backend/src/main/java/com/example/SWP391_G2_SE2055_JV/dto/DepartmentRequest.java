package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Tạo hoặc đổi tên Phòng ban — BR-ORG-06, BR-ORG-13.
 *
 * <p>Không có trường {@code active}: bật/tắt hiển thị đi qua endpoint riêng để thao tác
 * ẩn danh mục (BR-ORG-14) không lẫn với thao tác sửa tên.
 */
@Data
public class DepartmentRequest {

    @NotBlank(message = "Tên phòng ban là bắt buộc")
    @Size(max = 100, message = "Tên phòng ban tối đa 100 ký tự")
    private String name;
}
