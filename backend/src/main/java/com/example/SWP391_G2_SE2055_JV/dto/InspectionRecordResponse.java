package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.entity.InspectionRecord;
import com.example.SWP391_G2_SE2055_JV.enums.InspectionResult;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Biên bản một lần kiểm tra phòng sau dọn — S-13, và liên kết "xem lần kiểm tra trước" trên
 * thẻ việc dọn lại (BR-HK-06, BR-HK-08, BR-HK-12).
 *
 * <p>Nhãn tiếng Việt của {@code result} nằm ở frontend ({@code pages/rooms/roomLabels.js}),
 * cùng quy ước với {@link HousekeepingTaskResponse} và {@link RoomStatusHistoryResponse}.
 */
@Data
@Builder
public class InspectionRecordResponse {

    private UUID             id;
    private UUID             taskId;
    private UUID             roomId;

    /** Manager đã kiểm tra — BR-ROOM-02. */
    private UUID             inspectorId;

    private InspectionResult result;

    /** Có giá trị khi và chỉ khi {@code result = FAIL} — BR-HK-08. */
    private String           reason;

    private LocalDateTime    inspectedAt;

    /** Việc dọn lại sinh ra do kết quả FAIL — BR-HK-12. {@code null} khi PASS. */
    private UUID             nextTaskId;

    public static InspectionRecordResponse fromEntity(InspectionRecord record) {
        return InspectionRecordResponse.builder()
            .id(record.getId())
            .taskId(record.getTaskId())
            .roomId(record.getRoomId())
            .inspectorId(record.getInspectorId())
            .result(record.getResult())
            .reason(record.getReason())
            .inspectedAt(record.getInspectedAt())
            .nextTaskId(record.getNextTaskId())
            .build();
    }
}
