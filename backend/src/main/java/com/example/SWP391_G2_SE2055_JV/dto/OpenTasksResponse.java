package com.example.SWP391_G2_SE2055_JV.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * S-21 — việc đang chờ nhận của khách sạn mình (BR-HK-14), kèm luôn "bạn có tự nhận được không, nếu
 * không thì vì sao" (BR-HK-15, BR-HK-16). Màn hình chỉ hiện ra, không tự suy luật — cùng nguyên tắc
 * {@code RoomResponse.allowedTargets}.
 */
@Data
@Builder
public class OpenTasksResponse {

    /** Người đang đăng nhập có tự nhận việc được lúc này không. */
    private boolean canClaim;

    /** Vì sao chưa nhận được — câu cho người dùng đọc; {@code null} khi nhận được. */
    private String blockedReason;

    /**
     * Ngày làm của ca đang làm (đã vào, chưa kết thúc) — ca qua đêm từ hôm qua thì là hôm qua (BR-SCH-03).
     * {@code null} khi người đó không có ca nào đang làm.
     */
    private LocalDate workDay;

    /** Mọi việc «Chưa phân công» của khách sạn, kể cả việc dọn lại — BR-HK-14. */
    private List<HousekeepingTaskResponse> tasks;
}
