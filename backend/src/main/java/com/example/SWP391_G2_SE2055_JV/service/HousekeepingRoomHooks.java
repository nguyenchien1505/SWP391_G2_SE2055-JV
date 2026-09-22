package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.entity.HousekeepingTask;
import com.example.SWP391_G2_SE2055_JV.entity.Room;
import com.example.SWP391_G2_SE2055_JV.enums.HousekeepingTaskStatus;
import com.example.SWP391_G2_SE2055_JV.enums.HousekeepingTaskType;
import com.example.SWP391_G2_SE2055_JV.enums.TaskCancelReason;
import com.example.SWP391_G2_SE2055_JV.enums.TaskCreatedSource;
import com.example.SWP391_G2_SE2055_JV.repository.HousekeepingTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Phản ứng của Housekeeping khi PHÒNG đổi trạng thái — BR-HK-01, BR-HK-09, BR-HK-10.
 *
 * <p>Dành cho module Quản lý phòng gọi (trực tiếp, hoặc bọc trong event listener — chờ hai
 * bên thống nhất), NGAY TRONG transaction đổi trạng thái phòng để phòng và task luôn nhất
 * quán. Module phòng đã kiểm tra quyền và Tenant trước khi gọi, nên các hàm ở đây nhận
 * thẳng {@link Room} và không tự kiểm tra lại.
 *
 * <p>Tách khỏi {@link HousekeepingService} có chủ ý: HousekeepingService sẽ gọi
 * RoomStatusService ở giai đoạn 2; nếu RoomStatusService lại gọi ngược HousekeepingService
 * thì thành vòng phụ thuộc bean và Spring không khởi động được. Class này không phụ thuộc
 * gì bên phòng nên module phòng gọi vào không tạo vòng.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HousekeepingRoomHooks {

    private final HousekeepingTaskRepository taskRepository;

    /**
     * BR-HK-01, BR-ROOM-10: phòng vào "Chờ dọn" — khách check-out, phòng mới tạo, hoặc ra
     * khỏi "Không khả dụng" (BR-HK-09 "sinh task mới bình thường") — thì sinh task CHECKOUT
     * chưa phân công, nguồn CHECKOUT_AUTO.
     *
     * <p>Bỏ qua nếu phòng đã có task CHECKOUT đang mở: chính Housekeeping cũng đưa phòng về
     * "Chờ dọn" (kiểm tra không đạt thì đã tự sinh task mới; gỡ người thì task cũ vẫn mở).
     * Không bỏ qua sẽ sinh trùng, vi phạm BR-HK-11.
     *
     * @return task vừa sinh, hoặc rỗng nếu phòng đã có task CHECKOUT đang mở.
     */
    @Transactional
    public Optional<HousekeepingTask> onRoomBecameDirty(Room room) {
        if (taskRepository.existsByRoomIdAndTaskTypeAndStatusIn(
                room.getId(), HousekeepingTaskType.CHECKOUT, HousekeepingTaskStatus.OPEN_STATUSES)) {
            return Optional.empty();
        }

        HousekeepingTask task = taskRepository.save(HousekeepingTask.builder()
            .tenantId(room.getTenantId())
            .locationId(room.getLocationId())
            .roomId(room.getId())
            .taskType(HousekeepingTaskType.CHECKOUT)
            .status(HousekeepingTaskStatus.UNASSIGNED)
            .createdSource(TaskCreatedSource.CHECKOUT_AUTO)
            .build());

        log.info("Tự sinh task CHECKOUT {} cho phòng {}", task.getId(), room.getRoomNumber());
        return Optional.of(task);
    }

    /** BR-HK-09: phòng chuyển "Không khả dụng" thì MỌI task đang mở của phòng bị hủy. */
    @Transactional
    public int onRoomBecameUnavailable(Room room) {
        return cancel(
            taskRepository.findByRoomIdAndStatusIn(room.getId(), HousekeepingTaskStatus.OPEN_STATUSES),
            TaskCancelReason.ROOM_UNAVAILABLE);
    }

    /**
     * BR-HK-10: khách check-out khi task STAYOVER chưa xong thì task đó bị hủy. Trong cùng
     * lần check-out, module phòng gọi hàm này TRƯỚC {@link #onRoomBecameDirty}.
     */
    @Transactional
    public int onGuestCheckedOut(Room room) {
        return cancel(
            taskRepository.findByRoomIdAndTaskTypeAndStatusIn(
                room.getId(), HousekeepingTaskType.STAYOVER, HousekeepingTaskStatus.OPEN_STATUSES),
            TaskCancelReason.GUEST_CHECKED_OUT);
    }

    private int cancel(List<HousekeepingTask> tasks, TaskCancelReason reason) {
        LocalDateTime now = LocalDateTime.now();
        for (HousekeepingTask task : tasks) {
            task.setStatus(HousekeepingTaskStatus.CANCELLED);
            task.setCancelReason(reason);
            task.setCancelledAt(now);
        }
        taskRepository.saveAll(tasks);
        if (!tasks.isEmpty()) {
            log.info("Hủy {} task ({})", tasks.size(), reason);
        }
        return tasks.size();
    }
}
