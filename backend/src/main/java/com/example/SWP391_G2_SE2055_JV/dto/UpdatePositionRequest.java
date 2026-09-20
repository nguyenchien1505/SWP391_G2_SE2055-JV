package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.enums.PositionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Sửa Position — BR-ORG-07, BR-ORG-08.
 *
 * <p>Không cho đổi Phòng ban: BR-ORG-07 quy định Position thuộc đúng một Department
 * CỐ ĐỊNH, và Department của nhân viên được suy ra từ Position nên đổi ở đây sẽ âm thầm
 * chuyển phòng ban của mọi người đang giữ chức danh này.
 *
 * <p>{@code positionType} chỉ đổi được khi chưa ai được gán — xem service.
 */
@Data
public class UpdatePositionRequest {

    @NotBlank(message = "Tên chức danh là bắt buộc")
    @Size(max = 100, message = "Tên chức danh tối đa 100 ký tự")
    private String name;

    /** Bỏ trống nghĩa là giữ nguyên Loại chức danh. */
    private PositionType positionType;
}
