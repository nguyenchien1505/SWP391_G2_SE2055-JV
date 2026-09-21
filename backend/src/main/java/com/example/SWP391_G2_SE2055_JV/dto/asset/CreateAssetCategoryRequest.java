package com.example.SWP391_G2_SE2055_JV.dto.asset;

import com.example.SWP391_G2_SE2055_JV.enums.AssetKind;
import com.example.SWP391_G2_SE2055_JV.enums.AssetPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Tạo danh mục tài sản — BR-ASSET-08, BR-ASSET-09 (chỉ Giám đốc).
 *
 * <p>{@code assetKind} CHỈ có ở đây, không có trong {@link UpdateAssetCategoryRequest}:
 * đổi loại sau khi đã có tài sản cá thể hoặc dòng tồn kho trỏ vào sẽ làm dữ liệu mất
 * nghĩa (tài sản cố định thuộc một danh mục tiêu hao), và kéo theo {@code unit} bị xóa
 * trong khi tồn kho vẫn tham chiếu.
 */
@Data
public class CreateAssetCategoryRequest {

    @NotBlank(message = "Tên danh mục không được để trống")
    @Size(max = 100, message = "Tên danh mục không được vượt quá 100 ký tự")
    private String name;

    @NotNull(message = "Loại tài sản không được để trống")
    private AssetKind assetKind;

    @NotNull(message = "Mục đích sử dụng không được để trống")
    private AssetPurpose purpose;

    /** Bắt buộc khi {@code assetKind = CONSUMABLE}, phải bỏ trống khi FIXED — BR-ASSET-08. */
    @Size(max = 20, message = "Đơn vị tính không được vượt quá 20 ký tự")
    private String unit;
}
