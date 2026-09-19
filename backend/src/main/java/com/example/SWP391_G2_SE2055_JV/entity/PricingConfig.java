package com.example.SWP391_G2_SE2055_JV.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Bảng đơn giá chung do Admin Platform cấu hình — BR-SAAS-02.
 *
 * <p>Có hiệu lực theo {@code effectiveFrom} (unique): mỗi ngày hiệu lực đúng 1 bảng giá.
 * Thay đổi ở đây KHÔNG ảnh hưởng gói đã bán, vì {@link Subscription} chốt snapshot đơn
 * giá tại thời điểm mua (BR-SAAS-05).
 */
@Entity
@Table(name = "pricing_config")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class PricingConfig extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    /** VND / Location / chu kỳ — số nguyên (BR-SAAS-15). */
    @Column(name = "price_per_location", nullable = false)
    private Long pricePerLocation;

    /** VND / Staff / chu kỳ — Giám đốc và Manager không tính (BR-SAAS-03). */
    @Column(name = "price_per_user", nullable = false)
    private Long pricePerUser;

    /** VND / Phòng / chu kỳ. */
    @Column(name = "price_per_room", nullable = false)
    private Long pricePerRoom;

    /** Ngày bắt đầu hiệu lực, unique toàn bảng. */
    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;
}
