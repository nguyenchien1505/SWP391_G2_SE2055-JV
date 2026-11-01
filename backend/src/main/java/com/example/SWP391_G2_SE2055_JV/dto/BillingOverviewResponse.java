package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.dto.TenantUsageResponse.UsageMetric;
import com.example.SWP391_G2_SE2055_JV.enums.SuspendReason;
import com.example.SWP391_G2_SE2055_JV.enums.TenantStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * Màn "Gói dịch vụ" của Giám đốc: gói đang dùng, hạn dùng, mức sử dụng so với quota và bảng giá
 * hiện hành để tính tiền khi mua gói / mua thêm.
 */
@Data
@Builder
public class BillingOverviewResponse {

    private String        tenantName;
    private TenantStatus  status;
    /** Chỉ có giá trị khi {@code status = SUSPENDED} — BR-SAAS-16. */
    private SuspendReason suspendReason;

    /** Còn ở gói dùng thử (chưa trả phí lần nào) — BR-SAAS-08. */
    private boolean   trial;
    private LocalDate trialEndsAt;
    /** Số ngày dùng thử còn lại tính từ hôm nay (giờ Hà Nội), không âm; {@code null} nếu đã trả phí. */
    private Long      trialDaysLeft;

    /** Ngày đầu tiên CHƯA được trả tiền (đã trả đến hôm trước) — chỉ có khi đã trả phí (BR-SAAS-05). */
    private LocalDate nextBillingDate;

    /**
     * Ngày bắt đầu kỳ nếu gia hạn hôm nay: ngày đến hạn, cộng bù số ngày bị khóa nếu đang bị khóa vì
     * thanh toán lỗi. {@code null} khi còn dùng thử. FE hiển thị kỳ tới theo trường này.
     */
    private LocalDate nextPeriodStart;

    private int quotaLocation;
    private int quotaUser;
    private int quotaRoom;

    /**
     * Kỳ đã gia hạn trước gần nhất mà chưa bắt đầu: ngày bắt đầu và số lượng sẽ có hiệu lực từ ngày đó
     * (số chọn lúc gia hạn, cộng phần mua thêm đã trả phủ sang kỳ đó). {@code null} nếu không có. Cũng là
     * số "giữ nguyên" mặc định khi gia hạn tiếp.
     */
    private LocalDate plannedFrom;
    private Integer   plannedQuotaLocation;
    private Integer   plannedQuotaUser;
    private Integer   plannedQuotaRoom;

    /** Đơn giá đã chốt của gói (snapshot — BR-SAAS-05). */
    private Long pricePerLocation;
    private Long pricePerUser;
    private Long pricePerRoom;
    /** Phí mỗi chu kỳ = quota × đơn giá đã chốt. */
    private Long periodFee;

    private UsageMetric locations;
    /** Chỉ Staff — Giám đốc và Manager không tính (BR-SAAS-03). */
    private UsageMetric staff;
    private UsageMetric rooms;

    /** Bảng giá đang hiệu lực hôm nay — dùng khi mua gói lần đầu. */
    private PricingConfigResponse currentPricing;

    /** Số ngày dùng thử theo cấu hình hệ thống — hiển thị ở lựa chọn "Dùng thử" của Tenant mới. */
    private int trialDays;
}
