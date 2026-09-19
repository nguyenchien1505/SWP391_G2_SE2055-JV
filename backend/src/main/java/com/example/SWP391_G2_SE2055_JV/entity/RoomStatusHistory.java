package com.example.SWP391_G2_SE2055_JV.entity;

import com.example.SWP391_G2_SE2055_JV.enums.ChangeSource;
import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Nhật ký đổi trạng thái phòng — BR-ROOM-09, DM-06.
 *
 * <p>Mỗi lần {@link Room#getStatus()} thay đổi sinh ĐÚNG một dòng, kể cả khi hệ thống
 * tự chuyển. Bảng này là append-only: đã ghi thì không sửa, không xóa.
 *
 * <p>DM-06: lịch sử sử dụng phòng được SUY RA từ chuỗi bản ghi này — không có entity
 * RoomStay và không lưu bất kỳ thông tin khách nào.
 */
@Entity
@Table(name = "room_status_history")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class RoomStatusHistory extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 36, nullable = false)
    private UUID tenantId;

    @Column(name = "room_id", length = 36, nullable = false)
    private UUID roomId;

    /** NULL ở bản ghi đầu tiên của phòng mới tạo — BR-ROOM-10. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private RoomStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 20, nullable = false)
    private RoomStatus toStatus;

    /** NULL khi changeSource = SYSTEM (không có người thực hiện). */
    @Column(name = "changed_by", length = 36)
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    /** Cột "Người/nguồn thực hiện" của ma trận BR-ROOM-02. */
    @Enumerated(EnumType.STRING)
    @Column(name = "change_source", length = 20, nullable = false)
    private ChangeSource changeSource;

    @Column(name = "reason", length = 500)
    private String reason;

    /** Task dọn gây ra bước chuyển này, nếu có. */
    @Column(name = "related_task_id", length = 36)
    private UUID relatedTaskId;

    /** Bước chuyển do hệ thống tự thực hiện thì không có người chịu trách nhiệm. */
    public boolean isSystemChange() {
        return changeSource == ChangeSource.SYSTEM;
    }
}
