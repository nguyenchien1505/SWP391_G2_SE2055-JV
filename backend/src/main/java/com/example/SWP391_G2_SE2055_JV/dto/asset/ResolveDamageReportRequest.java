package com.example.SWP391_G2_SE2055_JV.dto.asset;

import com.example.SWP391_G2_SE2055_JV.enums.FixedAssetStatus;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Manager đóng báo hỏng — BR-ASSET-06, BR-ASSET-11. Cả hai trường đều không bắt buộc, body
 * cũng có thể bỏ trống.
 *
 * <p>{@code newAssetStatus} là lựa chọn CHỦ ĐỘNG của Manager ngay lúc đóng phiếu, không phải
 * hệ thống tự đổi (BR-ASSET-06). Bỏ trống = giữ nguyên trạng thái tài sản (ví dụ báo nhầm).
 * Luật chuyển trạng thái vẫn là của {@code FixedAssetService.updateStatus} (BR-ASSET-14).
 */
@Data
public class ResolveDamageReportRequest {

    private FixedAssetStatus newAssetStatus;

    @Size(max = 500, message = "Ghi chú xử lý không được vượt quá 500 ký tự")
    private String resolutionNote;
}
