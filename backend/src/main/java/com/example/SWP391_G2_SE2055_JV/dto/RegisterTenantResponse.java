package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.enums.TenantStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Kết quả đăng ký Tenant. KHÔNG chứa mật khẩu hay hash: người đăng ký đã tự đặt mật
 * khẩu, không có gì cần trả lại.
 */
@Data
@Builder
public class RegisterTenantResponse {

    private UUID         tenantId;
    private String       companyName;
    private TenantStatus status;
    private LocalDate    trialEndsAt;

    private UUID   directorId;
    private String directorEmail;

    private int quotaLocation;
    private int quotaUser;
    private int quotaRoom;
}
