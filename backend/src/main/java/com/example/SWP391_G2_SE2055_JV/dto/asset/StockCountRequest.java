package com.example.SWP391_G2_SE2055_JV.dto.asset;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Kiểm kê tồn kho theo đợt — BR-ASSET-04, BR-ASSET-07.
 *
 * <p>Một request ghi đè NHIỀU dòng cùng lúc, đúng tinh thần "một đợt kiểm kê" dù
 * Milestone 1 không có entity "Đợt kiểm kê" riêng. Toàn bộ chạy trong 1 transaction:
 * một dòng sai (không thuộc Location, id không tồn tại) thì rollback cả đợt, và mọi
 * dòng ghi cùng một mốc {@code lastCountedAt}/{@code lastCountedBy}.
 */
@Data
public class StockCountRequest {

    @NotEmpty(message = "Danh sách kiểm kê không được để trống")
    @Valid
    private List<Line> lines;

    @Data
    public static class Line {

        @NotNull(message = "ID dòng tồn kho không được để trống")
        private UUID itemId;

        @NotNull(message = "Số lượng không được để trống")
        @DecimalMin(value = "0", message = "Số lượng không được âm")
        @Digits(integer = 10, fraction = 2, message = "Số lượng chỉ cho phép tối đa 2 chữ số thập phân")
        private BigDecimal quantity;
    }
}
