package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.entity.HousekeepingTaskReturn;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/** Một lần trả lại việc dọn — "Lịch sử trả lại" trên thẻ việc của bảng điều phối (BR-HK-17). */
@Data
@Builder
public class TaskReturnResponse {

    private UUID          id;
    private UUID          taskId;
    private UUID          staffId;
    /** Trả sẵn tên vì nhân viên xem lịch sử không gọi được danh bạ nhân sự. */
    private String        staffName;
    private String        reason;
    private LocalDateTime returnedAt;
    /** {@code true} = phòng về «Chờ dọn»; {@code false} = nhóm còn người dọn tiếp. */
    private boolean       taskReleased;

    /** @param staffName có thể {@code null} nếu không tra được người trả */
    public static TaskReturnResponse fromEntity(HousekeepingTaskReturn taskReturn, String staffName) {
        return TaskReturnResponse.builder()
            .id(taskReturn.getId())
            .taskId(taskReturn.getTaskId())
            .staffId(taskReturn.getStaffId())
            .staffName(staffName)
            .reason(taskReturn.getReason())
            .returnedAt(taskReturn.getReturnedAt())
            .taskReleased(taskReturn.isTaskReleased())
            .build();
    }
}
