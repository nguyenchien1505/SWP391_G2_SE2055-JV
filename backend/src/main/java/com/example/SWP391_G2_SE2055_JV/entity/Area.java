package com.example.SWP391_G2_SE2055_JV.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * Khu vực trong khách sạn — BR-ORG-12, BR-ORG-15.
 *
 * <p>Khác 3 danh mục cấp Tenant (Department / Position / RoomType): Area tạo ở cấp
 * LOCATION vì là đặc thù vật lý của từng khách sạn, và tên chỉ unique trong phạm vi
 * Location (BR-ORG-13). Không có cờ is_active.
 */
@Entity
@Table(name = "areas")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Area extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 36, nullable = false)
    private UUID tenantId;

    /** Area thuộc đúng 1 Location — BR-ORG-12. */
    @Column(name = "location_id", length = 36, nullable = false)
    private UUID locationId;

    /** Unique theo (locationId, name) — BR-ORG-13. */
    @Column(name = "name", length = 100, nullable = false)
    private String name;
}
