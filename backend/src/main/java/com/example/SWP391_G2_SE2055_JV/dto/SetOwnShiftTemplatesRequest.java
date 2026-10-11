package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Bật / tắt bộ mẫu ca riêng cho một chi nhánh (V6). */
@Data
public class SetOwnShiftTemplatesRequest {

    /** true = chi nhánh chỉ dùng bộ mẫu riêng của nó; false = quay về bộ mẫu chung của chuỗi. */
    @NotNull(message = "Chọn bộ mẫu chi nhánh dùng")
    private Boolean ownShiftTemplates;
}
