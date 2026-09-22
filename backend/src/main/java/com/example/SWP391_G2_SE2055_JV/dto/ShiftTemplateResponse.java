package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.entity.ShiftTemplate;
import com.example.SWP391_G2_SE2055_JV.utils.ShiftTimeUtils;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
public class ShiftTemplateResponse {

    private UUID          id;
    private String        name;
    private LocalTime     startTime;
    private LocalTime     endTime;
    private String        description;

    /** BR-SCH-22: false = đã vô hiệu hóa, không xếp ca mới được nữa (không phải đã xóa). */
    private boolean       active;

    /** Suy ra từ giờ, trả kèm để màn hình xếp ca khỏi tự tính — BR-SCH-03. */
    private boolean       overnight;
    private BigDecimal    durationHours;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ShiftTemplateResponse fromEntity(ShiftTemplate template) {
        return ShiftTemplateResponse.builder()
            .id(template.getId())
            .name(template.getName())
            .startTime(template.getStartTime())
            .endTime(template.getEndTime())
            .description(template.getDescription())
            .active(template.isActive())
            .overnight(ShiftTimeUtils.isOvernight(template.getStartTime(), template.getEndTime()))
            .durationHours(ShiftTimeUtils.durationHours(template.getStartTime(), template.getEndTime()))
            .createdAt(template.getCreatedAt())
            .updatedAt(template.getUpdatedAt())
            .build();
    }
}
