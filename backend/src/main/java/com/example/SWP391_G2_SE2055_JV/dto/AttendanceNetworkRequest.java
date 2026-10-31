package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Body của {@code POST /scheduling/attendance/network/ips} — thêm một IP / dải IP mạng khách sạn. */
@Data
public class AttendanceNetworkRequest {

    @NotBlank(message = "Nhập IP hoặc dải IP")
    @Size(max = 50, message = "IP / dải IP tối đa 50 ký tự")
    private String cidr;

    @Size(max = 100, message = "Ghi chú tối đa 100 ký tự")
    private String label;
}
