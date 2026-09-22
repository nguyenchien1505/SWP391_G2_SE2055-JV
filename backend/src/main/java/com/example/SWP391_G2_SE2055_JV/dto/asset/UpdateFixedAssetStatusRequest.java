package com.example.SWP391_G2_SE2055_JV.dto.asset;

import com.example.SWP391_G2_SE2055_JV.enums.FixedAssetStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Đổi trạng thái tài sản cố định — BR-ASSET-02, BR-ASSET-14.
 *
 * <p>Nhận cả 4 giá trị. Ba trạng thái Tốt/Hỏng/Đang sửa chuyển tự do qua lại; riêng
 * {@code DISPOSED} là trạng thái cuối, vào rồi không ra được — service chặn, không
 * phải DTO.
 */
@Data
public class UpdateFixedAssetStatusRequest {

    @NotNull(message = "Trạng thái không được để trống")
    private FixedAssetStatus status;
}
