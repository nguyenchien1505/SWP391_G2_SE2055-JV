package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.entity.HousekeepingTask;
import com.example.SWP391_G2_SE2055_JV.entity.Room;
import com.example.SWP391_G2_SE2055_JV.enums.HousekeepingTaskStatus;
import com.example.SWP391_G2_SE2055_JV.enums.HousekeepingTaskType;
import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import com.example.SWP391_G2_SE2055_JV.enums.TaskCancelReason;
import com.example.SWP391_G2_SE2055_JV.enums.TaskCreatedSource;
import com.example.SWP391_G2_SE2055_JV.enums.UnassignedReason;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class HousekeepingTaskResponse {

    private UUID                   id;
    private UUID                   locationId;
    private UUID                   roomId;
    /** Số phòng, tầng và loại phòng để màn hình lịch dọn hiện "101 - Deluxe", không phải tra thêm. */
    private String                 roomNumber;
    private String                 floor;
    private String                 roomTypeName;
    /** Trạng thái HIỆN TẠI của phòng — lịch dọn theo nhân viên hiện cạnh số phòng. */
    private RoomStatus             roomStatus;
    private HousekeepingTaskType   taskType;
    private HousekeepingTaskStatus status;
    /** Nhóm người dọn — rỗng khi chưa phân công. */
    private List<UUID>             assignedStaffIds;
    /**
     * Cùng nhóm đó kèm họ tên, xếp theo tên. Trả sẵn vì màn "Việc dọn của tôi" của nhân viên không
     * gọi được danh bạ nhân sự mà vẫn cần biết mình dọn cùng ai.
     */
    private List<Assignee>         assignees;
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

    public record Assignee(UUID staffId, String fullName) {}

    /**
     * @param room         có thể {@code null} nếu phòng không còn tải được — khi đó bỏ trống số phòng
     * @param roomTypeName có thể {@code null}
     * @param staffNames   họ tên theo id; người không tra được (dữ liệu cũ) để tên trống
     */
    public static HousekeepingTaskResponse fromEntity(HousekeepingTask task, Room room, String roomTypeName,
                                                      Map<UUID, String> staffNames) {
        List<Assignee> assignees = task.getAssigneeIds().stream()
            .map(id -> new Assignee(id, staffNames.get(id)))
            .sorted(Comparator.comparing(a -> a.fullName() == null ? "" : a.fullName()))
            .toList();
        return HousekeepingTaskResponse.builder()
            .id(task.getId())
            .locationId(task.getLocationId())
            .roomId(task.getRoomId())
            .roomNumber(room == null ? null : room.getRoomNumber())
            .floor(room == null ? null : room.getFloor())
            .roomTypeName(roomTypeName)
            .roomStatus(room == null ? null : room.getStatus())
            .taskType(task.getTaskType())
            .status(task.getStatus())
            .assignedStaffIds(assignees.stream().map(Assignee::staffId).toList())
            .assignees(assignees)
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
