package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.enums.AttendanceMethod;
import lombok.Builder;
import lombok.Data;

/**
 * Một dòng của bảng chấm công Manager: ca (kèm tình trạng chấm công), tên người làm và giờ vào / ra hiện tại
 * do ai ghi — lần ghi cuối của từng loại.
 */
@Data
@Builder
public class AttendanceRowResponse {

    private ShiftResponse    shift;
    private String           staffName;
    /** Null khi chưa có giờ vào, hoặc giờ có từ trước khi có bảng log (dữ liệu cũ). */
    private AttendanceMethod checkInMethod;
    private AttendanceMethod checkOutMethod;
    /** Có lần tự vào / ra ca nào từ ngoài mạng khách sạn (chế độ kiểm IP «Cảnh báo»). */
    private boolean          outsideNetwork;
}
