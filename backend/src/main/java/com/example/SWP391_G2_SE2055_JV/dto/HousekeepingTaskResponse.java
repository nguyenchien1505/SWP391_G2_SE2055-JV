package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.entity.HousekeepingTask;
import com.example.SWP391_G2_SE2055_JV.entity.Room;
import com.example.SWP391_G2_SE2055_JV.enums.HousekeepingTaskStatus;
import com.example.SWP391_G2_SE2055_JV.enums.HousekeepingTaskType;
import com.example.SWP391_G2_SE2055_JV.enums.TaskCancelReason;
import com.example.SWP391_G2_SE2055_JV.enums.TaskCreatedSource;
import com.example.SWP391_G2_SE2055_JV.enums.UnassignedReason;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class HousekeepingTaskResponse {

    private UUID                   id;
    private UUID                   locationId;
    private UUID                   roomId;
    /** Số phòng và tầng để màn hình lịch dọn hiển thị, không phải tra thêm. */
    private String                 roomNumber;
    private String                 floor;
    private HousekeepingTaskType   taskType;
    private HousekeepingTaskStatus status;
    private UUID                   assignedStaffId;
    private LocalDate              assignedDate;
    private UUID                   assignedBy;
    private LocalDateTime          assignedAt;
    private LocalDateTime          completedAt;
    private TaskCreatedSource      createdSource;
    private UUID                   parentTaskId;
    private UnassignedReason       unassignedReason;
    private TaskCancelReason       cancelReason;
    private LocalDateTime          cancelledAt;
    private LocalDateTime          createdAt;

    /** @param room có thể {@code null} nếu phòng không còn tải được — khi đó bỏ trống số phòng. */
    public static HousekeepingTaskResponse fromEntity(HousekeepingTask task, Room room) {
        return HousekeepingTaskResponse.builder()
            .id(task.getId())
            .locationId(task.getLocationId())
            .roomId(task.getRoomId())
            .roomNumber(room == null ? null : room.getRoomNumber())
            .floor(room == null ? null : room.getFloor())
            .taskType(task.getTaskType())
            .status(task.getStatus())
            .assignedStaffId(task.getAssignedStaffId())
            .assignedDate(task.getAssignedDate())
            .assignedBy(task.getAssignedBy())
            .assignedAt(task.getAssignedAt())
            .completedAt(task.getCompletedAt())
            .createdSource(task.getCreatedSource())
            .parentTaskId(task.getParentTaskId())
            .unassignedReason(task.getUnassignedReason())
            .cancelReason(task.getCancelReason())
            .cancelledAt(task.getCancelledAt())
            .createdAt(task.getCreatedAt())
            .build();
    }
}
