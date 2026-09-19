package com.example.SWP391_G2_SE2055_JV.entity;

import com.example.SWP391_G2_SE2055_JV.enums.PositionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * Chức danh — danh mục cấp TENANT, thuộc đúng 1 Department (BR-ORG-06, BR-ORG-07).
 *
 * <p>Department của một nhân viên suy ra từ Position được gán, không lưu lại ở
 * {@code User} (BR-ORG-07).
 */
@Entity
@Table(name = "positions")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Position extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 36, nullable = false)
    private UUID tenantId;

    /** Mỗi Position thuộc đúng 1 Department — BR-ORG-07. */
    @Column(name = "department_id", length = 36, nullable = false)
    private UUID departmentId;

    /** Unique theo (tenantId, name) — BR-ORG-13. */
    @Column(name = "name", length = 100, nullable = false)
    private String name;

    /** Quyền nghiệp vụ đặc thù gán theo LOẠI này, không theo tên — BR-ORG-08. */
    @Enumerated(EnumType.STRING)
    @Column(name = "position_type", length = 20, nullable = false)
    private PositionType positionType;

    /** BR-ORG-14: ẩn thay vì xóa, vì BR-ORG-10 chặn cứng việc xóa danh mục đang dùng. */
    @Column(name = "is_active", nullable = false)
    @lombok.Builder.Default
    private boolean active = true;

    /** BR-PERM-04: chỉ Position loại Lễ tân mới được đổi trạng thái phòng. */
    public boolean isReception() {
        return positionType == PositionType.RECEPTION;
    }

    /** BR-PERM-05: chỉ Position loại Dọn dẹp mới nhận được task housekeeping. */
    public boolean isHousekeeping() {
        return positionType == PositionType.HOUSEKEEPING;
    }
}
