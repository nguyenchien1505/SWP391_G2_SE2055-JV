package com.example.SWP391_G2_SE2055_JV.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Mẫu ca làm việc dùng lại khi xếp lịch — BR-SCH-04.
 *
 * <p>Khai báo ở cấp TENANT, tên duy nhất trong Tenant. Chỉ là khuôn giờ: Manager vẫn
 * được tạo ca tự do không gắn mẫu (khi đó {@code Shift.sourceTemplateId} = null).
 */
@Entity
@Table(name = "shift_templates")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class ShiftTemplate extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 36, nullable = false)
    private UUID tenantId;

    /** Unique theo (tenant_id, name). */
    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "description", length = 500)
    private String description;

    /** BR-SCH-22: không xóa cứng mẫu ca, chỉ vô hiệu hóa để giữ ca lịch sử đã tham chiếu. */
    @Column(name = "is_active", nullable = false)
    @lombok.Builder.Default
    private boolean active = true;
}
