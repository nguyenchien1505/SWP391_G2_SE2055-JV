package com.example.SWP391_G2_SE2055_JV.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Kết quả xử lý khi VNPay trả Giám đốc về hệ thống. Số lượng của hóa đơn được trả kèm để FE điền
 * sẵn khi đưa Giám đốc về form thanh toán lại.
 */
@Data
@Builder
public class PaymentResultResponse {

    public enum Status {
        /** Thanh toán thành công (hoặc hóa đơn đã được xử lý thành công trước đó). */
        SUCCESS,
        /** Giao dịch không thành công — thanh toán lại được. */
        FAILED,
        /** Giám đốc tự hủy trên trang VNPay — thanh toán lại được. */
        CANCELLED,
        /** Kết quả không hợp lệ (sai chữ ký) — không thay đổi gì. */
        INVALID,
        /** Đã có tiền nhưng không áp được — cần đối soát, KHÔNG thanh toán lại. */
        NEEDS_REVIEW
    }

    private Status  status;
    private String  message;
    private Integer quotaLocation;
    private Integer quotaUser;
    private Integer quotaRoom;
}
