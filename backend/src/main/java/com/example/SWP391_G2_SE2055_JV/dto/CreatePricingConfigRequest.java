package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.time.LocalDate;

/**
 * Thêm một bảng đơn giá mới — BR-SAAS-02.
 *
 * <p>Bảng giá chỉ THÊM MỚI, không sửa, không xóa: Subscription đã chốt snapshot đơn giá lúc
 * mua (BR-SAAS-05) nên đổi giá chỉ áp cho gói mua/nâng từ ngày hiệu lực trở đi.
 *
 * <p>Tiền là {@code Long} vì VND là số nguyên, không thập phân (BR-SAAS-15).
 *
 * <p>Trần 1 tỷ VND / đơn vị chặn nhập nhầm và tránh tràn số khi các chức năng sau nhân giá
 * với hạn mức để ra tổng tiền. Đây là con số team đề xuất, không phải quy định trong BR.
 *
 * <p>Ràng buộc "ngày hiệu lực không ở quá khứ" KHÔNG đặt ở đây bằng {@code @FutureOrPresent}:
 * annotation đó lấy ngày theo múi giờ của server, trong khi team chốt dùng giờ Hà Nội
 * (xem {@code ShiftTimeUtils.todayInHanoi()}). Việc kiểm tra nằm ở service.
 */
@Data
public class CreatePricingConfigRequest {

    private static final long MAX_UNIT_PRICE = 1_000_000_000L;

    @NotNull(message = "Đơn giá mỗi Location là bắt buộc")
    @PositiveOrZero(message = "Đơn giá mỗi Location không được âm")
    @Max(value = MAX_UNIT_PRICE, message = "Đơn giá mỗi Location tối đa 1.000.000.000 VND")
    private Long pricePerLocation;

    @NotNull(message = "Đơn giá mỗi Staff là bắt buộc")
    @PositiveOrZero(message = "Đơn giá mỗi Staff không được âm")
    @Max(value = MAX_UNIT_PRICE, message = "Đơn giá mỗi Staff tối đa 1.000.000.000 VND")
    private Long pricePerUser;

    @NotNull(message = "Đơn giá mỗi Phòng là bắt buộc")
    @PositiveOrZero(message = "Đơn giá mỗi Phòng không được âm")
    @Max(value = MAX_UNIT_PRICE, message = "Đơn giá mỗi Phòng tối đa 1.000.000.000 VND")
    private Long pricePerRoom;

    @NotNull(message = "Ngày bắt đầu hiệu lực là bắt buộc")
    private LocalDate effectiveFrom;
}
