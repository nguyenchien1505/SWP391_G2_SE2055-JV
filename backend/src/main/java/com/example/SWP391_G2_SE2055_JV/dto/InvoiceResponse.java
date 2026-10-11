package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.entity.Invoice;
import com.example.SWP391_G2_SE2055_JV.enums.InvoiceStatus;
import com.example.SWP391_G2_SE2055_JV.enums.InvoiceType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Một dòng trong lịch sử hóa đơn của Giám đốc — BR-SAAS-15. */
@Data
@Builder
public class InvoiceResponse {

    private UUID          id;
    private InvoiceType   invoiceType;
    private LocalDate     periodStart;
    private LocalDate     periodEnd;
    private int           quotaLocation;
    private int           quotaUser;
    private int           quotaRoom;
    private Long          amount;
    private InvoiceStatus status;
    private LocalDateTime issuedAt;
    private LocalDateTime paidAt;
    private LocalDate     graceUntil;

    public static InvoiceResponse fromEntity(Invoice invoice) {
        return InvoiceResponse.builder()
            .id(invoice.getId())
            .invoiceType(invoice.getInvoiceType())
            .periodStart(invoice.getPeriodStart())
            .periodEnd(invoice.getPeriodEnd())
            .quotaLocation(invoice.getQuotaLocationSnapshot())
            .quotaUser(invoice.getQuotaUserSnapshot())
            .quotaRoom(invoice.getQuotaRoomSnapshot())
            .amount(invoice.getAmount())
            .status(invoice.getStatus())
            .issuedAt(invoice.getIssuedAt())
            .paidAt(invoice.getPaidAt())
            .graceUntil(invoice.getGraceUntil())
            .build();
    }
}
