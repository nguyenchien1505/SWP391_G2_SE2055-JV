package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.entity.ShiftAttendanceEvent;
import com.example.SWP391_G2_SE2055_JV.enums.AttendanceEventType;
import com.example.SWP391_G2_SE2055_JV.enums.AttendanceMethod;
import com.example.SWP391_G2_SE2055_JV.enums.NetworkCheckResult;
import com.example.SWP391_G2_SE2055_JV.enums.VerificationType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/** Một lần ghi chấm công — lịch sử cho Manager đối soát. */
@Data
@Builder
public class AttendanceEventResponse {

    private UUID                id;
    private AttendanceEventType eventType;
    private LocalDateTime       occurredAt;
    private LocalDateTime       recordedAt;
    private AttendanceMethod    method;
    private UUID                recordedBy;
    private String              recordedByName;
    private String              ipAddress;
    private String              userAgent;
    private String              deviceId;
    private VerificationType    verificationType;
    private NetworkCheckResult  networkCheck;
    private String              matchedCidr;
    private String              note;

    public static AttendanceEventResponse fromEntity(ShiftAttendanceEvent event, String recordedByName) {
        return AttendanceEventResponse.builder()
            .id(event.getId())
            .eventType(event.getEventType())
            .occurredAt(event.getOccurredAt())
            .recordedAt(event.getRecordedAt())
            .method(event.getMethod())
            .recordedBy(event.getRecordedBy())
            .recordedByName(recordedByName)
            .ipAddress(event.getIpAddress())
            .userAgent(event.getUserAgent())
            .deviceId(event.getDeviceId())
            .verificationType(event.getVerificationType())
            .networkCheck(event.getNetworkCheck())
            .matchedCidr(event.getMatchedCidr())
            .note(event.getNote())
            .build();
    }
}
