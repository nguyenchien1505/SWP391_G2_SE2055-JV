package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Tạo Location — BR-ORG-04.
 *
 * <p>Không có trường "Tổng số phòng": đó là derived field, đếm theo số bản ghi Phòng
 * thực tế chứ không nhập tay. Không có trường trạng thái: Location mới luôn ở
 * "Chưa vận hành" cho tới khi có Manager (BR-ORG-02, DM-13).
 */
@Data
public class CreateLocationRequest {

    @NotBlank(message = "Tên khách sạn là bắt buộc")
    @Size(max = 255, message = "Tên khách sạn tối đa 255 ký tự")
    private String name;

    @NotBlank(message = "Địa chỉ là bắt buộc")
    @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự")
    private String address;

    @NotBlank(message = "Số điện thoại liên hệ là bắt buộc")
    @Size(max = 30, message = "Số điện thoại tối đa 30 ký tự")
    private String phone;

    /** BR-ORG-04: thị trường mục tiêu 2-3 sao; cho phép 1-5 để không chặn oan. */
    @Min(value = 1, message = "Hạng sao phải từ 1 đến 5")
    @Max(value = 5, message = "Hạng sao phải từ 1 đến 5")
    private Integer starRating;

    /**
     * Mốc xác định "ca tương lai" của Location này — BR-SCH-17. Bỏ trống thì dùng
     * {@code Asia/Ho_Chi_Minh}.
     */
    @Size(max = 64, message = "Múi giờ tối đa 64 ký tự")
    private String timezone;
}
