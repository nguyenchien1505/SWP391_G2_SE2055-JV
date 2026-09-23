package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.enums.InspectionResult;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Body của {@code POST /housekeeping/tasks/{id}/inspection} — Manager nghiệm thu phòng sau khi
 * nhân viên báo dọn xong (BR-HK-06, BR-HK-08).
 *
 * <p>Giá trị enum sai (ví dụ {@code "MAYBE"}) bị chặn ngay lúc đọc JSON → 400.
 */
@Data
public class InspectTaskRequest {

    @NotNull(message = "Chưa chọn kết quả kiểm tra")
    private InspectionResult result;

    /**
     * Bắt buộc khi {@code result = FAIL} (BR-HK-08) — ràng buộc có điều kiện nên kiểm tra ở
     * service; DB cũng ép bằng {@code ck_inspection_fail_reason}. Text tự do, KHÔNG phải danh
     * mục lỗi. 500 ký tự khớp cột {@code inspection_records.reason}.
     */
    @Size(max = 500, message = "Lý do tối đa 500 ký tự")
    private String reason;
}
