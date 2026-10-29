package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/** Nhân viên A đề nghị đổi ca của mình lấy một ca của đồng nghiệp B — BR-SCH-09, DM-08. */
@Data
public class CreateShiftSwapRequest {

    @NotNull(message = "Chọn ca của bạn muốn đổi")
    private UUID requesterShiftId;

    @NotNull(message = "Chọn ca của đồng nghiệp muốn nhận")
    private UUID peerShiftId;
}
