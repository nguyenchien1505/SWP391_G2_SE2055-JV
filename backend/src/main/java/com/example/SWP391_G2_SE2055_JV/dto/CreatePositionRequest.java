package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.enums.PositionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * Tạo Position — BR-ORG-06, BR-ORG-07, BR-ORG-08.
 */
@Data
public class CreatePositionRequest {

    /** BR-ORG-07: Position thuộc đúng 1 Department, chọn lúc tạo và không đổi về sau. */
    @NotNull(message = "Phòng ban là bắt buộc")
    private UUID departmentId;

    @NotBlank(message = "Tên chức danh là bắt buộc")
    @Size(max = 100, message = "Tên chức danh tối đa 100 ký tự")
    private String name;

    /** BR-ORG-08: quyền nghiệp vụ đặc thù gán theo LOẠI này, không theo tên. */
    @NotNull(message = "Loại chức danh là bắt buộc")
    private PositionType positionType;
}
