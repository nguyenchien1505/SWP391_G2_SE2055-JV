package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.enums.SwapRequestStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/** Yêu cầu đổi ca kèm người và ca của hai phía — đủ để hiện "ca A ↔ ca B" mà không gọi thêm API. */
@Data
@Builder
public class ShiftSwapRequestResponse {
    private UUID              id;
    private UUID              locationId;
    private SwapRequestStatus status;
    /** Người gửi (A) và ca A đem đi đổi. */
    private Party             requester;
    /** Đồng nghiệp (B) và ca A muốn nhận. */
    private Party             peer;
    /** Hạn để B trả lời, chốt lúc gửi — BR-SCH-21. */
    private LocalDateTime     expiresAt;
    private LocalDateTime     peerRespondedAt;
    private UUID              approverId;
    private String            approverName;
    private LocalDateTime     decidedAt;
    private LocalDateTime     createdAt;

    @Data
    @Builder
    public static class Party {
        private UUID          staffId;
        private String        fullName;
        private ShiftResponse shift;
    }
}
