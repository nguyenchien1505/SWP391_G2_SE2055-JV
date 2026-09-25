package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Sửa toàn bộ thông tin Location — chỉ Giám đốc (BR-ORG-03, BR-ORG-04).
 *
 * <p>Trạng thái vận hành KHÔNG sửa tay ở đây: nó đi theo việc Location có Manager hay
 * không (BR-ORG-02, DM-13).
 */
@Data
public class UpdateLocationRequest {

    @NotBlank(message = "Tên khách sạn là bắt buộc")
    @Size(max = 255, message = "Tên khách sạn tối đa 255 ký tự")
    private String name;

    @NotBlank(message = "Địa chỉ là bắt buộc")
    @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự")
    private String address;

    @NotBlank(message = "Số điện thoại liên hệ là bắt buộc")
    @Size(max = 30, message = "Số điện thoại tối đa 30 ký tự")
    private String phone;

    /**
     * Để 1–5 cho khách sạn cũ đã lưu 4–5 sao vẫn sửa được thông tin khác; ĐỔI sang hạng trên 3
     * sao bị chặn ở {@code LocationService}, cùng mức với lúc tạo.
     */
    @Min(value = 1, message = "Hạng sao phải từ 1 đến 5")
    @Max(value = 5, message = "Hạng sao phải từ 1 đến 5")
    private Integer starRating;

    /** Chỉ nhận giờ Hà Nội ({@code Asia/Ho_Chi_Minh}) — xem {@code LocationService}. */
    @NotBlank(message = "Múi giờ là bắt buộc")
    @Size(max = 64, message = "Múi giờ tối đa 64 ký tự")
    private String timezone;
}
