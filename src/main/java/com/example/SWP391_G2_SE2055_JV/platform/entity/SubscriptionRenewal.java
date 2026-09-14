package com.example.SWP391_G2_SE2055_JV.platform.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscription_renewals")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SubscriptionRenewal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Column(name = "invoice_id")
    private Long invoiceId;

    @Column(name = "previous_end_date")
    private LocalDate previousEndDate;

    @Column(name = "new_end_date")
    private LocalDate newEndDate;

    @Column(name = "renewal_type")
    private String renewalType;

    private String status;

    @Column(name = "renewed_by")
    private Long renewedBy;

    @Column(name = "renewed_at")
    private LocalDateTime renewedAt;
}
