package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Tạo một ca. BR-SCH-04 cho hai cách tạo, chọn ĐÚNG MỘT:
 * <ul>
 *   <li>Theo mẫu — truyền {@code sourceTemplateId}, giờ lấy từ mẫu, KHÔNG gửi kèm
 *       {@code startTime}/{@code endTime}.</li>
 *   <li>Ca tự do — bỏ trống {@code sourceTemplateId} và tự nhập cả hai mốc giờ.</li>
 * </ul>
 * Cả hai đều đi qua kiểm tra Schedule Policy (BR-SCH-02).
 *
 * <p>Giờ để {@code null} ở đây vì cách thứ nhất không cần; ràng buộc "có mẫu hoặc có giờ"
 * kiểm ở service để báo lỗi nói rõ thiếu gì.
 */
@Data
public class CreateShiftRequest {

    @NotNull(message = "locationId là bắt buộc")
    private UUID locationId;

    /** Bỏ trống để tạo ca CHƯA PHÂN CÔNG — DM-03. */
    private UUID staffId;

    @NotNull(message = "shiftDate là bắt buộc")
    private LocalDate shiftDate;

    private LocalTime startTime;

    private LocalTime endTime;

    /** Bỏ trống nghĩa là ca tự do, không theo mẫu — BR-SCH-04. */
    private UUID sourceTemplateId;
}
