package com.example.SWP391_G2_SE2055_JV.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * Một ca của đồng nghiệp mà nhân viên đổi được với ca của mình. Trả kèm họ tên vì nhân viên không gọi
 * được {@code GET /users} và chỉ xem được ca của chính mình.
 */
@Data
@Builder
public class SwapCandidateResponse {
    private UUID          staffId;
    private String        fullName;
    private ShiftResponse shift;
}
