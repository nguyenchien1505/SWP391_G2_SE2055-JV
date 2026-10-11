package com.example.SWP391_G2_SE2055_JV.dto;

/**
 * Kết quả bấm thanh toán (mua gói / gia hạn / mua thêm):
 * <ul>
 *   <li>{@code paymentUrl} có giá trị → chuyển sang trang VNPay.</li>
 *   <li>{@code paymentUrl} = null → không mở giao dịch mới, hiện {@code message}: giao dịch trước đó
 *       hóa ra đã thanh toán ({@code activated} = true, gói đã được cập nhật), đang xử lý, hoặc cần
 *       kiểm tra.</li>
 * </ul>
 */
public record PaymentUrlResponse(String paymentUrl, String message, boolean activated) {

    public static PaymentUrlResponse redirect(String paymentUrl) {
        return new PaymentUrlResponse(paymentUrl, null, false);
    }

    public static PaymentUrlResponse message(String message, boolean activated) {
        return new PaymentUrlResponse(null, message, activated);
    }
}
