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
 * <p>Giám đốc quản lý. Mẫu thuộc bộ CHUNG của chuỗi ({@code locationId = null}) hoặc bộ RIÊNG của một
 * chi nhánh (V5). Mỗi chi nhánh dùng đúng một bộ, theo cờ {@code Location.ownShiftTemplates} (V6, chốt
 * 06/10/2026); tên không trùng trong cùng một bộ. Mẫu không chuyển được sang bộ khác. Chỉ là khuôn giờ:
 * Manager vẫn được tạo ca tự do không gắn mẫu (khi đó {@code Shift.sourceTemplateId} = null).
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

    /** NULL = bộ mẫu chung của chuỗi; có giá trị = bộ mẫu riêng của chi nhánh đó. Không đổi sau khi tạo. */
    @Column(name = "location_id", length = 36, updatable = false)
    private UUID locationId;

    /** Unique trong cùng một bộ (tenant + chi nhánh hoặc chung) — cột sinh scope_key ở V5. */
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
