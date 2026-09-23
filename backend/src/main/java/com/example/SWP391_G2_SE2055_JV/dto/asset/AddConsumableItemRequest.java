package com.example.SWP391_G2_SE2055_JV.dto.asset;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Thêm một danh mục tiêu hao vào kho của Location — bước khởi tạo dòng tồn kho, tách
 * biệt với kiểm kê định kỳ ({@link StockCountRequest}).
 *
 * <p>{@code locationId} lấy từ session, không nhận từ body — giống lối
 * {@code FixedAssetRequest} (BR-ASSET-13).
 */
@Data
public class AddConsumableItemRequest {

    @NotNull(message = "Danh mục tài sản không được để trống")
    private UUID categoryId;

    /** Số lượng ban đầu — cũng được xem là lần kiểm kê đầu tiên (BR-ASSET-04, 07). */
    @NotNull(message = "Số lượng không được để trống")
    @DecimalMin(value = "0", message = "Số lượng không được âm")
    @Digits(integer = 10, fraction = 2, message = "Số lượng chỉ cho phép tối đa 2 chữ số thập phân")
    private BigDecimal quantity;
}
