package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Bật/tắt hiển thị một danh mục cấp Tenant — BR-ORG-14.
 *
 * <p>Dùng chung cho Department, Position và Loại phòng: BR-ORG-10 chặn cứng việc xóa
 * danh mục đang được tham chiếu, nên "ẩn" là cách duy nhất để loại một mục không còn
 * dùng ra khỏi danh sách chọn.
 */
@Data
public class SetActiveRequest {

    @NotNull(message = "Giá trị active là bắt buộc")
    private Boolean active;
}
