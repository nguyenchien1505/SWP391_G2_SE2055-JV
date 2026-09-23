package com.example.SWP391_G2_SE2055_JV.dto.asset;

import com.example.SWP391_G2_SE2055_JV.enums.AssetPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Sửa danh mục tài sản — BR-ASSET-09.
 *
 * <p>KHÔNG có {@code assetKind} (bất biến, xem {@link CreateAssetCategoryRequest}) và
 * KHÔNG có {@code active}: việc ẩn/hiện theo BR-ORG-14 đi qua endpoint riêng để thao
 * tác đó hiện rõ trong log và không lẫn vào một lần sửa tên thông thường.
 */
@Data
public class UpdateAssetCategoryRequest {

    @NotBlank(message = "Tên danh mục không được để trống")
    @Size(max = 100, message = "Tên danh mục không được vượt quá 100 ký tự")
    private String name;

    @NotNull(message = "Mục đích sử dụng không được để trống")
    private AssetPurpose purpose;

    /** Chỉ có nghĩa với danh mục loại CONSUMABLE — BR-ASSET-08. */
    @Size(max = 20, message = "Đơn vị tính không được vượt quá 20 ký tự")
    private String unit;
}
