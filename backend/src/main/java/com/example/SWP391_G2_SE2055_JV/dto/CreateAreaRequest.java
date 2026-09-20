package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * Tạo Khu vực — BR-ORG-12, BR-ORG-13.
 *
 * <p>Khu vực thuộc cấp LOCATION. Manager bỏ trống {@code locationId} vì luôn thao tác
 * trong Location của mình; Giám đốc bắt buộc chỉ rõ.
 */
@Data
public class CreateAreaRequest {

    private UUID locationId;

    @NotBlank(message = "Tên khu vực là bắt buộc")
    @Size(max = 100, message = "Tên khu vực tối đa 100 ký tự")
    private String name;
}
