package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.entity.LeaveRequest;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.LeaveRequestStatus;
import com.example.SWP391_G2_SE2055_JV.enums.LeaveType;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Một đơn xin nghỉ kèm sẵn tên người gửi, vị trí, khách sạn và người duyệt — màn hình không phải tra thêm
 * (nhân viên không đọc được danh sách nhân sự).
 */
@Data
@Builder
public class LeaveRequestResponse {

    private UUID               id;
    private LeaveType          leaveType;
    private LocalDate          fromDate;
    private LocalDate          toDate;
    /** Số ngày nghỉ, tính cả hai đầu (DM-07). */
    private int                days;
    private String             reason;
    private LeaveRequestStatus status;

    private UUID               requesterId;
    private String             requesterName;
    private String             requesterEmail;
    private Role               requesterRole;
    /** Vị trí của người gửi; null với Manager (DM-01: Manager không có Position). */
    private String             positionName;

    private UUID               locationId;
    private String             locationName;

    private UUID               approverId;
    private String             approverName;
    private LocalDateTime      decidedAt;
    private LocalDateTime      createdAt;

    /**
     * @param requester    hồ sơ người gửi; null nếu không còn tra được
     * @param positionName vị trí của người gửi
     * @param approver     người duyệt / từ chối; null khi đơn chưa có quyết định
     */
    public static LeaveRequestResponse fromEntity(LeaveRequest leave, User requester, String positionName,
                                                  String locationName, User approver) {
        return LeaveRequestResponse.builder()
            .id(leave.getId())
            .leaveType(leave.getLeaveType())
            .fromDate(leave.getFromDate())
            .toDate(leave.getToDate())
            .days(leave.days())
            .reason(leave.getReason())
            .status(leave.getStatus())
            .requesterId(leave.getRequesterId())
            .requesterName(requester == null ? null : requester.getFullName())
            .requesterEmail(requester == null ? null : requester.getEmail())
            .requesterRole(requester == null ? null : requester.getRole())
            .positionName(positionName)
            .locationId(leave.getLocationId())
            .locationName(locationName)
            .approverId(leave.getApproverId())
            .approverName(approver == null ? null : approver.getFullName())
            .decidedAt(leave.getDecidedAt())
            .createdAt(leave.getCreatedAt())
            .build();
    }
}
