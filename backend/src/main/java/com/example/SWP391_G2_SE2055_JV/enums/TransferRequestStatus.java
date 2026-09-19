package com.example.SWP391_G2_SE2055_JV.enums;

/** Trạng thái yêu cầu điều chuyển nhân sự — BR-TRF-07 (đúng 5 trạng thái). */
public enum TransferRequestStatus {

    /** Chờ Giám đốc duyệt. Manager được tự hủy yêu cầu của mình ở trạng thái này. */
    PENDING,

    /**
     * Đã duyệt nhưng chưa tới effective date. Giai đoạn này chỉ được xếp ca ở
     * Location cũ cho thời điểm TRƯỚC effective date — BR-TRF-04.
     */
    APPROVED,

    REJECTED,

    /** Job đã chạy tại effective date và chuyển Location xong — BR-TRF-05. */
    EXECUTED,

    CANCELLED
}
