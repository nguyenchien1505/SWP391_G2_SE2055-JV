package com.example.SWP391_G2_SE2055_JV.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * Một IP hoặc dải IP mạng của khách sạn — nhân viên vào / ra ca từ mạng này mới tính là «tại khách sạn»
 * (chấm công M2, chốt 01/11/2026). Manager của khách sạn khai.
 */
@Entity
@Table(name = "location_attendance_networks")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class LocationAttendanceNetwork extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 36, nullable = false)
    private UUID tenantId;

    @Column(name = "location_id", length = 36, nullable = false)
    private UUID locationId;

    /** Đã chuẩn hóa bởi {@code IpRange} — "113.160.92.0/24", "2001:ee0:4f1::/64", "113.160.92.202". */
    @Column(name = "cidr", length = 50, nullable = false)
    private String cidr;

    @Column(name = "label", length = 100)
    private String label;
}
