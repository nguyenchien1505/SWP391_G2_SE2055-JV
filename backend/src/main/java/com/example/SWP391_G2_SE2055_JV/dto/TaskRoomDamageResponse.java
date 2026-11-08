package com.example.SWP391_G2_SE2055_JV.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Một tài sản trong phòng của việc dọn đang có báo hỏng chờ xử lý — để người dọn khỏi báo trùng thứ đã có
 * người báo (chốt 02/11/2026). Chỉ số đếm và "có phải mình báo không": người dọn vẫn chỉ đọc được phiếu do
 * chính mình gửi ở «Báo hỏng của tôi» (BR-ASSET-05), không lộ người báo hay nội dung phiếu của người khác.
 */
@Data
@Builder
public class TaskRoomDamageResponse {

    private UUID          fixedAssetId;
    /** Số phiếu đang chờ xử lý của tài sản này — luôn từ 1 trở lên. */
    private int           openReports;
    /** Trong các phiếu đó có phiếu do chính người đang xem gửi. */
    private boolean       reportedByMe;
    private LocalDateTime lastReportedAt;
}
