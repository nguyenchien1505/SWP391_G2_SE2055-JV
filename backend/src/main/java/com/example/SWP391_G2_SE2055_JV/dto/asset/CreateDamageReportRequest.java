package com.example.SWP391_G2_SE2055_JV.dto.asset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * Báo hỏng tài sản cố định — BR-ASSET-05, BR-ASSET-06.
 *
 * <p>{@code locationId} và {@code reporterId} lấy từ session, không nhận từ body —
 * cùng lối với {@code FixedAssetRequest} (BR-ASSET-13).
 */
@Data
public class CreateDamageReportRequest {

    @NotNull(message = "Tài sản cố định không được để trống")
    private UUID fixedAssetId;

    /** Bắt buộc: Manager cần biết hỏng gì để quyết định (BR-ASSET-06). */
    @NotBlank(message = "Mô tả tình trạng hỏng không được để trống")
    @Size(max = 500, message = "Mô tả không được vượt quá 500 ký tự")
    private String description;
}
