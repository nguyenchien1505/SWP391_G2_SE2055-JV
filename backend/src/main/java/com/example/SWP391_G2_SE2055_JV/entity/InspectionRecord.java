package com.example.SWP391_G2_SE2055_JV.entity;

import com.example.SWP391_G2_SE2055_JV.enums.InspectionResult;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Biên bản Manager kiểm tra phòng sau khi dọn — BR-HK-06, BR-HK-08, DM-06.
 *
 * <p>Chỉ task CHECKOUT mới đi qua bước kiểm tra; STAYOVER bỏ qua (BR-HK-06).
 *
 * <p>Kiểm tra KHÔNG đạt vẫn đưa task gốc sang COMPLETED — không có trạng thái FAILED.
 * Kết quả FAIL nằm ở đây, và {@code nextTaskId} trỏ tới task dọn lại vừa được sinh
 * (BR-HK-12).
 */
@Entity
@Table(name = "inspection_records")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class InspectionRecord extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 36, nullable = false)
    private UUID tenantId;

    /** Task dọn được kiểm tra. */
    @Column(name = "task_id", length = 36, nullable = false)
    private UUID taskId;

    @Column(name = "room_id", length = 36, nullable = false)
    private UUID roomId;

    /** Chỉ Manager mới được kiểm tra phòng — BR-ROOM-02. */
    @Column(name = "inspector_id", length = 36, nullable = false)
    private UUID inspectorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", length = 10, nullable = false)
    private InspectionResult result;

    /** Bắt buộc khi FAIL, text tự do (không phải danh mục) — BR-HK-08. */
    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "inspected_at", nullable = false)
    private LocalDateTime inspectedAt;

    /** Task dọn lại sinh ra do kết quả FAIL — BR-HK-12. NULL khi PASS. */
    @Column(name = "next_task_id", length = 36)
    private UUID nextTaskId;

    /** BR-HK-08: FAIL thì bắt buộc có lý do và phòng quay về Chờ dọn. */
    public boolean isFailed() {
        return result == InspectionResult.FAIL;
    }

    public boolean isPassed() {
        return result == InspectionResult.PASS;
    }
}
