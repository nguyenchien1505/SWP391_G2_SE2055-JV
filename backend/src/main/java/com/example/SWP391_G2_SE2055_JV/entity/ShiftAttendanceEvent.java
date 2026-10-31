package com.example.SWP391_G2_SE2055_JV.entity;

import com.example.SWP391_G2_SE2055_JV.enums.AttendanceEventType;
import com.example.SWP391_G2_SE2055_JV.enums.AttendanceMethod;
import com.example.SWP391_G2_SE2055_JV.enums.NetworkCheckResult;
import com.example.SWP391_G2_SE2055_JV.enums.VerificationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Một lần ghi giờ vào / ra ca — chấm công M2 (sửa DM-15, chốt 31/10/2026).
 *
 * <p>Giờ HIỆU LỰC vẫn nằm trên {@link Shift#getCheckInAt()} / {@link Shift#getCheckOutAt()}; bảng này giữ MỌI
 * lần ghi (nhân viên tự bấm, Manager nhập bù, Manager sửa) kèm thiết bị đã dùng, để đối soát về sau. Chỉ thêm,
 * không sửa hay xóa.
 */
@Entity
@Table(name = "shift_attendance_events")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class ShiftAttendanceEvent extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 36, nullable = false)
    private UUID tenantId;

    @Column(name = "shift_id", length = 36, nullable = false)
    private UUID shiftId;

    /** Người làm ca lúc ghi. */
    @Column(name = "staff_id", length = 36, nullable = false)
    private UUID staffId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 10, nullable = false)
    private AttendanceEventType eventType;

    /** Giờ vào / ra hiệu lực, giờ khách sạn. */
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    /** Lúc ghi, giờ khách sạn — với Manager nhập bù thì khác {@link #occurredAt}. */
    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", length = 10, nullable = false)
    private AttendanceMethod method;

    @Column(name = "recorded_by", length = 36, nullable = false)
    private UUID recordedBy;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    /** Mã trình duyệt tự sinh phía giao diện — giả mạo được, chỉ để tham khảo. */
    @Column(name = "device_id", length = 64)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_type", length = 20, nullable = false)
    @lombok.Builder.Default
    private VerificationType verificationType = VerificationType.NONE;

    /** Kết quả kiểm IP mạng khách sạn (V15). */
    @Enumerated(EnumType.STRING)
    @Column(name = "network_check", length = 15, nullable = false)
    @lombok.Builder.Default
    private NetworkCheckResult networkCheck = NetworkCheckResult.NOT_CHECKED;

    /** IP / dải IP đã khớp khi {@link NetworkCheckResult#MATCHED}. */
    @Column(name = "matched_cidr", length = 50)
    private String matchedCidr;

    /** Lý do — bắt buộc khi {@link AttendanceMethod#MANAGER}. */
    @Column(name = "note", length = 500)
    private String note;
}
