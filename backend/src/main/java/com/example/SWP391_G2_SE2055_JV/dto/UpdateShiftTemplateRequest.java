package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalTime;

/**
 * Sửa mẫu ca — BR-SCH-22.
 *
 * <p>Sửa giờ ở đây KHÔNG động tới các ca đã xếp từ mẫu này: ca đã lưu giữ nguyên giờ của
 * nó, vì đổi lại hàng loạt sẽ phá vỡ ràng buộc Schedule Policy vốn đã được kiểm tại thời
 * điểm xếp ca (BR-SCH-02). Mẫu chỉ là khuôn giờ cho ca xếp về sau.
 *
 * <p>Bật/tắt trạng thái dùng endpoint riêng, không lẫn vào đây.
 */
@Data
public class UpdateShiftTemplateRequest {

    @NotBlank(message = "Tên mẫu ca là bắt buộc")
    @Size(max = 100, message = "Tên mẫu ca tối đa 100 ký tự")
    private String name;

    @NotNull(message = "Giờ bắt đầu là bắt buộc")
    private LocalTime startTime;

    @NotNull(message = "Giờ kết thúc là bắt buộc")
    private LocalTime endTime;

    @Size(max = 500, message = "Mô tả tối đa 500 ký tự")
    private String description;
}
