package com.example.SWP391_G2_SE2055_JV.exception;

/**
 * Yêu cầu đổi ca vừa bị chuyển «Tự hủy» ngay trong lúc người dùng thao tác (quá hạn, ca đã đổi người,
 * đã tới giờ ca…). Là {@link BusinessException} nên trả 400 kèm lý do như mọi lỗi nghiệp vụ, nhưng
 * tách riêng để service đánh dấu {@code noRollbackFor}: trạng thái «Tự hủy» vẫn được lưu dù thao tác
 * của người dùng bị từ chối.
 */
public class SwapExpiredException extends BusinessException {
    public SwapExpiredException(String message) {
        super(message);
    }
}
