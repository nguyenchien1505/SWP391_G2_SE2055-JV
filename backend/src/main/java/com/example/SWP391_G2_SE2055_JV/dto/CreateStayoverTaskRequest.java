package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Manager tạo task dọn hằng ngày cho phòng đang có khách — BR-HK-05.
 *
 * <p>Chỉ có loại STAYOVER tạo tay; task CHECKOUT do hệ thống tự sinh khi phòng chuyển
 * "Chờ dọn" (BR-HK-01), không có API tạo.
 */
@Data
public class CreateStayoverTaskRequest {

    @NotNull(message = "roomId là bắt buộc")
    private UUID roomId;
}
