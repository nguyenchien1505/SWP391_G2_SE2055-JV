package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.entity.RoomStatusHistory;
import com.example.SWP391_G2_SE2055_JV.enums.ChangeSource;
import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Một dòng của S-05 Lịch sử trạng thái phòng — BR-ROOM-09.
 *
 * <p>Nhãn tiếng Việt của {@code fromStatus}, {@code toStatus}, {@code changeSource} nằm ở
 * frontend ({@code pages/rooms/roomLabels.js}), như {@link RoomResponse}.
 */
@Data
@Builder
public class RoomStatusHistoryResponse {

    private UUID          id;

    /** {@code null} ở dòng đầu tiên của phòng mới tạo — BR-ROOM-10. */
    private RoomStatus    fromStatus;
    private RoomStatus    toStatus;

    /** {@code null} khi {@code changeSource = SYSTEM}: không có người thực hiện. */
    private UUID          changedBy;
    private String        changedByName;

    private LocalDateTime changedAt;
    private ChangeSource  changeSource;
    private String        reason;

    /** Task dọn gây ra bước chuyển (assign, hoàn thành, kiểm tra…), nếu có. */
    private UUID          relatedTaskId;

    /** @param changedByName họ tên người thực hiện đã tra sẵn; {@code null} nếu là hệ thống. */
    public static RoomStatusHistoryResponse fromEntity(RoomStatusHistory history, String changedByName) {
        return RoomStatusHistoryResponse.builder()
            .id(history.getId())
            .fromStatus(history.getFromStatus())
            .toStatus(history.getToStatus())
            .changedBy(history.getChangedBy())
            .changedByName(changedByName)
            .changedAt(history.getChangedAt())
            .changeSource(history.getChangeSource())
            .reason(history.getReason())
            .relatedTaskId(history.getRelatedTaskId())
            .build();
    }
}
