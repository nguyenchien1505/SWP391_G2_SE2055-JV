package com.example.SWP391_G2_SE2055_JV.dto.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Tạo / sửa Khu vực — BR-ORG-12.
 *
 * <p>Không nhận {@code locationId}: Khu vực luôn thuộc Location của Manager đang thao
 * tác, lấy từ session — giống lối {@code FixedAssetRequest} không nhận
 * {@code locationId} từ client (BR-ASSET-13).
 */
@Data
public class AreaRequest {

    @NotBlank(message = "Tên khu vực không được để trống")
    @Size(max = 100, message = "Tên khu vực không được vượt quá 100 ký tự")
    private String name;
}
