package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Duyệt đơn nghỉ — kèm lễ tân thay cho từng ca theo mẫu sẽ mất lễ tân duy nhất khi gỡ người nghỉ. BR-SCH-29 sửa
 * (chốt 31/10/2026): mỗi ca theo mẫu phải có lễ tân, nên duyệt đơn của lễ tân không còn "chỉ cảnh báo" — phải xếp
 * người thay rồi mới duyệt được. Đơn không làm ca nào thiếu lễ tân thì không cần gửi body.
 */
@Data
public class ApproveLeaveRequestRequest {

    @Valid
    private List<ReceptionReplacement> replacements = new ArrayList<>();

    /** Xếp {@code staffId} vào ca {@code shiftId} — chính ca của người nghỉ, thay cho người đó. */
    @Data
    public static class ReceptionReplacement {

        @NotNull(message = "Thiếu ca cần xếp lễ tân thay")
        private UUID shiftId;

        @NotNull(message = "Chọn lễ tân thay cho ca này")
        private UUID staffId;
    }
}
