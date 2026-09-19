package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/** Gán người cho một ca đang ở trạng thái chưa phân công — DM-03. */
@Data
public class AssignShiftRequest {

    @NotNull(message = "staffId là bắt buộc")
    private UUID staffId;
}
