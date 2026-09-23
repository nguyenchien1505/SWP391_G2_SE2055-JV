package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.entity.PricingConfig;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

/** Một bảng đơn giá trả về client — BR-SAAS-02. Đơn vị: VND / chu kỳ 30 ngày. */
@Data
@Builder
public class PricingConfigResponse {

    private UUID      id;
    private Long      pricePerLocation;
    private Long      pricePerUser;
    private Long      pricePerRoom;
    private LocalDate effectiveFrom;

    public static PricingConfigResponse fromEntity(PricingConfig pricing) {
        return PricingConfigResponse.builder()
            .id(pricing.getId())
            .pricePerLocation(pricing.getPricePerLocation())
            .pricePerUser(pricing.getPricePerUser())
            .pricePerRoom(pricing.getPricePerRoom())
            .effectiveFrom(pricing.getEffectiveFrom())
            .build();
    }
}
