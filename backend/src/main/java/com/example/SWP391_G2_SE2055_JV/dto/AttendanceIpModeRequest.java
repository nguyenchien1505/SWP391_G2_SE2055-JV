package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.enums.AttendanceIpMode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Body của {@code PUT /scheduling/attendance/network/mode}. */
@Data
public class AttendanceIpModeRequest {

    @NotNull(message = "Chọn chế độ kiểm IP")
    private AttendanceIpMode mode;
}
