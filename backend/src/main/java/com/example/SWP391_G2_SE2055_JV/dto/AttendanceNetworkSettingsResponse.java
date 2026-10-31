package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.enums.AttendanceIpMode;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Cài đặt mạng chấm công của một khách sạn, kèm IP của chính người đang xem — để Manager đứng tại khách sạn
 * bấm «Thêm IP mạng hiện tại» mà không cần biết IP là gì.
 */
@Data
@Builder
public class AttendanceNetworkSettingsResponse {

    private UUID             locationId;
    private AttendanceIpMode mode;
    private List<Network>    networks;
    /** IP server thấy được của người đang xem. */
    private String           currentIp;
    /** {@link #currentIp} đã nằm trong danh sách chưa. */
    private boolean          currentIpAllowed;
    /**
     * Mạng sẽ thêm khi bấm «Thêm IP mạng hiện tại»: IPv4 đúng {@link #currentIp}, IPv6 cả dải /64 chứa nó (IPv6
     * của từng máy đổi định kỳ). Null khi không đọc được IP.
     */
    private String           suggestedCidr;

    @Data
    @Builder
    public static class Network {
        private UUID          id;
        private String        cidr;
        private String        label;
        private LocalDateTime createdAt;
    }
}
