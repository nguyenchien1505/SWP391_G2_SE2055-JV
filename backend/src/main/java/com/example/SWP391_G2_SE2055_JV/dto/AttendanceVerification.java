package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Trường xác thực khi vào / ra ca — để trống ở M2. Sau này mã QR gửi {@code type = "QR"} kèm mã quét được
 * trong {@code payload}; kiểm theo IP thì server tự đọc IP, không cần client gửi gì.
 *
 * <p>{@code type} là chuỗi chứ không phải enum: client gửi một cách chưa hỗ trợ thì nhận câu báo rõ ràng
 * thay vì lỗi đọc JSON.
 */
@Data
public class AttendanceVerification {

    @Size(max = 20, message = "Loại xác thực tối đa 20 ký tự")
    private String type;

    @Size(max = 2000, message = "Dữ liệu xác thực tối đa 2000 ký tự")
    private String payload;
}
