package com.example.SWP391_G2_SE2055_JV.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * Loại phòng — danh mục cấp TENANT, dùng chung mọi Location (BR-ORG-11).
 *
 * <p>Tên unique trong phạm vi Tenant (BR-ORG-13).
 */
@Entity
@Table(name = "room_types")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class RoomType extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    /** Danh mục cấp Tenant, không gắn Location — BR-ORG-11. */
    @Column(name = "tenant_id", length = 36, nullable = false)
    private UUID tenantId;

    /** Unique theo (tenantId, name) — BR-ORG-13. */
    @Column(name = "name", length = 100, nullable = false)
    private String name;

    /** BR-ORG-14: ẩn thay vì xóa, vì BR-ORG-10 chặn cứng việc xóa danh mục đang dùng. */
    @Column(name = "is_active", nullable = false)
    @lombok.Builder.Default
    private boolean active = true;
}
