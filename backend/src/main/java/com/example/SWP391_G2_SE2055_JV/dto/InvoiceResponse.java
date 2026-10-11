package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.entity.Invoice;
import com.example.SWP391_G2_SE2055_JV.enums.InvoiceStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Một dòng trong lịch sử hóa đơn của Giám đốc — BR-SAAS-15. */
@Data
@Builder
public class InvoiceResponse {

    /** Nghĩa nghiệp vụ: mua gói và gia hạn cùng là PERIODIC trong DB, backend phân biệt sẵn. */
    public enum Kind { MUA_GOI, GIA_HAN, MUA_THEM }

    private UUID          id;
    private Kind          kind;
    private LocalDate     periodStart;
    private LocalDate     periodEnd;
    private int           quotaLocation;
    private int           quotaUser;
    private int           quotaRoom;
    private Long          amount;
    private InvoiceStatus status;
    private LocalDateTime issuedAt;

    public static InvoiceResponse fromEntity(Invoice invoice, Kind kind) {
        return InvoiceResponse.builder()
            .id(invoice.getId())
            .kind(kind)
            .periodStart(invoice.getPeriodStart())
            .periodEnd(invoice.getPeriodEnd())
            .quotaLocation(invoice.getQuotaLocationSnapshot())
            .quotaUser(invoice.getQuotaUserSnapshot())
            .quotaRoom(invoice.getQuotaRoomSnapshot())
            .amount(invoice.getAmount())
            .status(invoice.getStatus())
            .issuedAt(invoice.getIssuedAt())
            .build();
    }
}
