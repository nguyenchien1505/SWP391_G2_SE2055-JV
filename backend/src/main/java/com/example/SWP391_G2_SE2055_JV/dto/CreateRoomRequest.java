package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * Body của {@code POST /rooms} — Giám đốc tạo phòng (BR-ROOM-04, BR-ROOM-05).
 *
 * <p>KHÔNG có {@code status}: phòng mới luôn vào «Chờ dọn» do backend đặt (BR-ROOM-10), client
 * không chọn được. Giới hạn độ dài khớp đúng cột trong bảng {@code rooms} để lỗi hiện ở tầng
 * validate (422) thay vì rơi xuống ràng buộc DB (409).
 */
@Data
public class CreateRoomRequest {

    /**
     * Phòng thuộc về một khách sạn cụ thể. Bắt buộc kể cả với Giám đốc vì vai trò này đứng ở
     * phạm vi toàn Tenant, không có Location mặc định.
     */
    @NotNull(message = "Chưa chọn khách sạn cho phòng")
    private UUID locationId;

    @NotBlank(message = "Số phòng là bắt buộc")
    @Size(max = 20, message = "Số phòng tối đa 20 ký tự")
    private String roomNumber;

    /** Kiểu text chứ không phải số — chấp nhận G, M, B1 (BR-ROOM-05). */
    @NotBlank(message = "Tầng là bắt buộc")
    @Size(max = 10, message = "Tầng tối đa 10 ký tự")
    private String floor;

    @NotNull(message = "Chưa chọn loại phòng")
    private UUID roomTypeId;

    /** Kiểu bọc để phân biệt "không gửi" (422 thiếu trường) với giá trị 0 (422 sai giá trị). */
    @NotNull(message = "Sức chứa là bắt buộc")
    @Positive(message = "Sức chứa phải lớn hơn 0")
    private Integer capacity;

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String note;
}
