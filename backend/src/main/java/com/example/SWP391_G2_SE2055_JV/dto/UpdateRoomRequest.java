package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * Body của {@code PUT /rooms/{id}} — Giám đốc sửa thông tin CẤU TRÚC của phòng (BR-ROOM-04,
 * BR-ROOM-05).
 *
 * <p>Cố ý KHÔNG có {@code locationId}: nghiệp vụ không có thao tác chuyển phòng sang khách sạn
 * khác — phòng là tài sản vật lý của một khách sạn. Cũng không có {@code status}: trạng thái chỉ
 * đổi qua {@code PATCH /rooms/{id}/status} và các thao tác trên task dọn (BR-ROOM-02).
 */
@Data
public class UpdateRoomRequest {

    @NotBlank(message = "Số phòng là bắt buộc")
    @Size(max = 20, message = "Số phòng tối đa 20 ký tự")
    private String roomNumber;

    /** Kiểu text chứ không phải số — chấp nhận G, M, B1 (BR-ROOM-05). */
    @NotBlank(message = "Tầng là bắt buộc")
    @Size(max = 10, message = "Tầng tối đa 10 ký tự")
    private String floor;

    @NotNull(message = "Chưa chọn loại phòng")
    private UUID roomTypeId;

    @NotNull(message = "Sức chứa là bắt buộc")
    @Positive(message = "Sức chứa phải lớn hơn 0")
    private Integer capacity;

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String note;
}
