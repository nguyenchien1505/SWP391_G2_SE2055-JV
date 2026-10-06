package com.example.SWP391_G2_SE2055_JV.entity;

import com.example.SWP391_G2_SE2055_JV.enums.LocationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * Một cơ sở khách sạn thuộc Tenant — BR-ORG-01.
 *
 * <p>Location mới tạo ở trạng thái NOT_OPERATIONAL cho tới khi được gán Manager VÀ tới ngày
 * Manager đó bắt đầu làm việc (BR-ORG-02, DM-13, chốt 06/10/2026 — xem
 * {@code LocationActivationService}). Số lượng Location bị giới hạn bởi quota gói dịch vụ
 * (BR-SAAS-02).
 */
@Entity
@Table(name = "locations")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Location extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 36, nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "address", length = 500, nullable = false)
    private String address;

    @Column(name = "phone", length = 30, nullable = false)
    private String phone;

    /** BR-ORG-04: thị trường mục tiêu 2-3 sao; DB nới 1-5, siết ở tầng validate. */
    @Column(name = "star_rating")
    private Integer starRating;

    /** Mốc xác định "ca tương lai" khi kiểm tra ràng buộc lịch — BR-SCH-17. */
    @Column(name = "timezone", length = 64, nullable = false)
    @lombok.Builder.Default
    private String timezone = "Asia/Ho_Chi_Minh";

    /** NOT_OPERATIONAL khi chưa có Manager, hoặc Manager chưa tới ngày bắt đầu làm — DM-13, BR-ORG-02. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private LocationStatus status;

    /**
     * Bộ mẫu ca chi nhánh đang dùng (V6, chốt 06/10/2026): false = bộ mẫu chung của chuỗi, true =
     * bộ mẫu riêng của chính chi nhánh. Giám đốc bật / tắt ở màn Quy định & Mẫu ca.
     */
    @Column(name = "own_shift_templates", nullable = false)
    @lombok.Builder.Default
    private boolean ownShiftTemplates = false;

    /** BR-ORG-02: chưa có Manager thì Location chưa được vận hành chính thức. */
    public boolean isOperational() {
        return status == LocationStatus.OPERATIONAL;
    }
}
