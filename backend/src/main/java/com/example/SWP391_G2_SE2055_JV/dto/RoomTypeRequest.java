package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Tạo hoặc đổi tên Loại phòng — BR-ORG-11, BR-ORG-13.
 *
 * <p>Danh mục cấp Tenant, dùng chung mọi Location, nên không có trường locationId.
 * Giá phòng không thuộc Milestone 1 (không có nghiệp vụ đặt phòng — BR-OUT-01).
 */
@Data
public class RoomTypeRequest {

    @NotBlank(message = "Tên loại phòng là bắt buộc")
    @Size(max = 100, message = "Tên loại phòng tối đa 100 ký tự")
    private String name;
}
