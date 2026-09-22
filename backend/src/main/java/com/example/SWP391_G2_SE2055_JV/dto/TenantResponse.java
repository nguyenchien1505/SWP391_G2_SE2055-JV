package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.entity.Tenant;
import com.example.SWP391_G2_SE2055_JV.enums.SuspendReason;
import com.example.SWP391_G2_SE2055_JV.enums.TenantStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Một dòng trong danh sách Tenant của Admin Platform.
 *
 * <p>Cố ý KHÔNG kèm thông tin gói dịch vụ: nạp gói cho từng dòng sẽ tốn thêm truy vấn ở
 * mỗi Tenant. Thông tin gói nằm ở {@link TenantDetailResponse}.
 *
 * <p>{@code suspendReason} chỉ có giá trị khi {@code status = SUSPENDED} — BR-SAAS-16.
 */
@Data
@Builder
public class TenantResponse {

    private UUID          id;
    private String        name;
    /** Cũng là email đăng nhập của Giám đốc — BR-SAAS-13. */
    private String        contactEmail;
    private String        contactPhone;
    private TenantStatus  status;
    private SuspendReason suspendReason;
    private LocalDateTime createdAt;

    public static TenantResponse fromEntity(Tenant tenant) {
        return TenantResponse.builder()
            .id(tenant.getId())
            .name(tenant.getName())
            .contactEmail(tenant.getContactEmail())
            .contactPhone(tenant.getContactPhone())
            .status(tenant.getStatus())
            .suspendReason(tenant.getSuspendReason())
            .createdAt(tenant.getCreatedAt())
            .build();
    }
}
