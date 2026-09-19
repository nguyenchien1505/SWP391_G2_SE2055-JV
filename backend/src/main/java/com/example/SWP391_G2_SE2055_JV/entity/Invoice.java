package com.example.SWP391_G2_SE2055_JV.entity;

import com.example.SWP391_G2_SE2055_JV.enums.InvoiceStatus;
import com.example.SWP391_G2_SE2055_JV.enums.InvoiceType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Hóa đơn NỘI BỘ của hệ thống SaaS — không phải hóa đơn điện tử (BR-SAAS-15).
 *
 * <p>Ba cột {@code quota*Snapshot} chốt lại quota tại thời điểm phát hành: BR-SAAS-06
 * cho phép ghi đè quota giữa kỳ, nếu không chốt thì hóa đơn cũ không giải thích được
 * đã tính trên cơ sở nào.
 */
@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Invoice extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 36, nullable = false)
    private UUID tenantId;

    @Column(name = "subscription_id", length = 36, nullable = false)
    private UUID subscriptionId;

    /** PERIODIC (chu kỳ) hoặc UPGRADE_DIFF (chênh lệch tăng gói) — BR-SAAS-15. */
    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_type", length = 20, nullable = false)
    private InvoiceType invoiceType;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    /** Quota được chốt lại tại thời điểm phát hành — BR-SAAS-06. */
    @Column(name = "quota_location_snapshot", nullable = false)
    private int quotaLocationSnapshot;

    @Column(name = "quota_user_snapshot", nullable = false)
    private int quotaUserSnapshot;

    @Column(name = "quota_room_snapshot", nullable = false)
    private int quotaRoomSnapshot;

    /** VND số nguyên, không tách VAT — BR-SAAS-15. */
    @Column(name = "amount", nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private InvoiceStatus status;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /** Mốc bắt đầu ân hạn, chỉ set ở lần thanh toán thất bại ĐẦU TIÊN — BR-SAAS-10. */
    @Column(name = "first_failed_at")
    private LocalDateTime firstFailedAt;

    /** Hết ngày này mà chưa PAID thì Tenant bị SUSPENDED — BR-SAAS-10. */
    @Column(name = "grace_until")
    private LocalDate graceUntil;

    public boolean isPending() {
        return status == InvoiceStatus.PENDING;
    }

    public boolean isPaid() {
        return status == InvoiceStatus.PAID;
    }
}
