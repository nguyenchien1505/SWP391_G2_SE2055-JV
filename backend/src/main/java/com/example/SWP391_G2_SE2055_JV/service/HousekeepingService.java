package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.AssignTaskRequest;
import com.example.SWP391_G2_SE2055_JV.dto.CreateStayoverTaskRequest;
import com.example.SWP391_G2_SE2055_JV.dto.HousekeepingTaskResponse;
import com.example.SWP391_G2_SE2055_JV.entity.HousekeepingTask;
import com.example.SWP391_G2_SE2055_JV.entity.Position;
import com.example.SWP391_G2_SE2055_JV.entity.Room;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.HousekeepingTaskStatus;
import com.example.SWP391_G2_SE2055_JV.enums.HousekeepingTaskType;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import com.example.SWP391_G2_SE2055_JV.enums.TaskCreatedSource;
import com.example.SWP391_G2_SE2055_JV.enums.UnassignedReason;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.HousekeepingTaskRepository;
import com.example.SWP391_G2_SE2055_JV.repository.PositionRepository;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftRepository;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import com.example.SWP391_G2_SE2055_JV.utils.ShiftTimeUtils;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lịch dọn phòng — BR-HK-01..12, DM-04, DM-05.
 *
 * <p><b>Giai đoạn 1 — chưa tích hợp module Quản lý phòng</b> (người khác đang làm).
 * Luồng STAYOVER chạy trọn vì phòng giữ nguyên "Đang sử dụng" suốt vòng đời task
 * (BR-HK-05). Luồng CHECKOUT thì MỖI bước phải đổi trạng thái phòng (BR-ROOM-02) và ghi
 * lịch sử (BR-ROOM-09) qua RoomStatusService của module kia, nên mọi bước CHECKOUT dừng
 * ở {@link #requireRoomIntegration}. Giai đoạn 2: thay từng lời gọi hàm đó bằng lời gọi
 * đổi trạng thái phòng ghi chú ngay tại chỗ.
 *
 * <p>Phản ứng khi PHÒNG đổi trạng thái (sinh/hủy task) nằm ở {@link HousekeepingRoomHooks}.
 *
 * <p>Mọi truy vấn đều lọc theo tenantId lấy từ session — không dùng {@code findAll()} trần.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HousekeepingService {

    private static final Set<HousekeepingTaskStatus> OPEN = HousekeepingTaskStatus.OPEN_STATUSES;

    private final HousekeepingTaskRepository taskRepository;
    private final UserRepository             userRepository;
    private final PositionRepository         positionRepository;
    private final ShiftRepository            shiftRepository;
    private final EntityManager              entityManager;

    /**
     * Lịch dọn. Nhân viên chỉ thấy task của chính mình (BR-PERM-05); Manager chỉ thấy task
     * trong Location của mình; Giám đốc thấy toàn Tenant.
     */
    @Transactional(readOnly = true)
    public Page<HousekeepingTaskResponse> getTasks(HousekeepingTaskStatus status,
                                                   HousekeepingTaskType taskType,
                                                   LocalDate assignedDate,
                                                   Pageable pageable) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID locationId = null;
        UUID staffId = null;
        if (SecurityUtils.hasRole(Role.STAFF)) {
            staffId = SecurityUtils.getCurrentUserId();
        } else if (SecurityUtils.hasRole(Role.MANAGER)) {
            locationId = SecurityUtils.getCurrentLocationId();
        }

        Page<HousekeepingTask> page = taskRepository.search(
            tenantId, locationId, staffId, status, taskType, assignedDate, pageable);
        Map<UUID, Room> rooms = loadRooms(page.getContent());
        return page.map(task -> HousekeepingTaskResponse.fromEntity(task, rooms.get(task.getRoomId())));
    }

    /** Cùng phạm vi với {@link #getTasks}; ngoài phạm vi trả 404 để không lộ task có tồn tại. */
    @Transactional(readOnly = true)
    public HousekeepingTaskResponse getTask(UUID id) {
        HousekeepingTask task = getOwnedTask(id);

        boolean outOfScope =
            (SecurityUtils.hasRole(Role.STAFF)
                && !SecurityUtils.getCurrentUserId().equals(task.getAssignedStaffId()))
            || (SecurityUtils.hasRole(Role.MANAGER)
                && !SecurityUtils.getCurrentLocationId().equals(task.getLocationId()));
        if (outOfScope) {
            throw new ResourceNotFoundException("HousekeepingTask", "id", id);
        }
        return toResponse(task);
    }

    /**
     * BR-HK-05: Manager tạo task dọn hằng ngày cho phòng đang có khách. Phòng giữ nguyên
     * "Đang sử dụng"; task CHECKOUT thì không tạo tay được — hệ thống tự sinh (BR-HK-01).
     */
    @Transactional
    public HousekeepingTaskResponse createStayoverTask(CreateStayoverTaskRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Room room = loadRoom(request.getRoomId(), tenantId);
        assertManagesLocation(room.getLocationId());

        if (room.getStatus() != RoomStatus.OCCUPIED) {
            throw new BusinessException("Chỉ tạo task dọn hằng ngày cho phòng đang có khách (BR-HK-05).");
        }
        if (taskRepository.existsByRoomIdAndTaskTypeAndStatusIn(
                room.getId(), HousekeepingTaskType.STAYOVER, OPEN)) {
            throw new BusinessException("Phòng này đã có task dọn hằng ngày đang mở (BR-HK-11).");
        }

        HousekeepingTask saved = taskRepository.save(HousekeepingTask.builder()
            .tenantId(tenantId)
            .locationId(room.getLocationId())
            .roomId(room.getId())
            .taskType(HousekeepingTaskType.STAYOVER)
            .status(HousekeepingTaskStatus.UNASSIGNED)
            .createdSource(TaskCreatedSource.MANAGER_STAYOVER)
            .build());

        log.info("Tạo task STAYOVER {} cho phòng {}", saved.getId(), room.getRoomNumber());
        return HousekeepingTaskResponse.fromEntity(saved, room);
    }

    /** BR-HK-02: Manager gán thủ công, không có gợi ý tự động phân bổ. */
    @Transactional
    public HousekeepingTaskResponse assignTask(UUID id, AssignTaskRequest request) {
        HousekeepingTask task = getOwnedTask(id);
        assertManagesLocation(task.getLocationId());

        if (task.getStatus() != HousekeepingTaskStatus.UNASSIGNED) {
            throw new BusinessException(task.isOpen()
                ? "Task đã có người phụ trách. Gỡ người hiện tại trước khi gán người mới."
                : "Task đã đóng, không gán người được nữa.");
        }
        assertCanReceiveTask(request.getStaffId(), task, request.getAssignedDate());
        requireRoomIntegration(task);   // CHECKOUT: phòng Chờ dọn → Đang dọn (BR-ROOM-02)

        task.setAssignedStaffId(request.getStaffId());
        task.setAssignedDate(request.getAssignedDate());
        task.setAssignedBy(SecurityUtils.getCurrentUserId());
        task.setAssignedAt(LocalDateTime.now());
        task.setUnassignedReason(null);
        // BR-HK-06: không có trạng thái "đã gán" riêng — gán là bắt đầu thực hiện.
        task.setStatus(HousekeepingTaskStatus.IN_PROGRESS);

        log.info("Gán task {} cho staff {} ngày {}", id, request.getStaffId(), request.getAssignedDate());
        return toResponse(taskRepository.save(task));
    }

    /**
     * Manager gỡ người khỏi task — BR-PERM-03 "điều chỉnh lịch dọn". Task về Chưa phân
     * công với lý do MANAGER_MANUAL (cùng bộ lý do với ca — BR-SCH-24).
     */
    @Transactional
    public HousekeepingTaskResponse unassignTask(UUID id) {
        HousekeepingTask task = getOwnedTask(id);
        assertManagesLocation(task.getLocationId());

        if (task.getStatus() != HousekeepingTaskStatus.IN_PROGRESS) {
            throw new BusinessException("Chỉ gỡ người khỏi task đang thực hiện.");
        }
        requireRoomIntegration(task);   // CHECKOUT: phòng Đang dọn → Chờ dọn (BR-ROOM-02)

        release(task, UnassignedReason.MANAGER_MANUAL);
        log.info("Gỡ người khỏi task {}", id);
        return toResponse(taskRepository.save(task));
    }

    /**
     * Nhân viên dọn bấm hoàn thành task của CHÍNH MÌNH — BR-PERM-05, BR-ROOM-02.
     * BR-HK-06: STAYOVER hoàn thành luôn, không qua bước Manager kiểm tra.
     */
    @Transactional
    public HousekeepingTaskResponse completeTask(UUID id) {
        HousekeepingTask task = getOwnedTask(id);

        if (!SecurityUtils.getCurrentUserId().equals(task.getAssignedStaffId())) {
            throw new BusinessException("Chỉ nhân viên được phân công mới bấm hoàn thành task này.");
        }
        if (task.getStatus() != HousekeepingTaskStatus.IN_PROGRESS) {
            throw new BusinessException("Task không ở trạng thái đang thực hiện.");
        }
        requireRoomIntegration(task);   // CHECKOUT: task → Chờ kiểm tra, phòng Đang dọn → Chờ kiểm tra

        task.setStatus(HousekeepingTaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now());

        log.info("Hoàn thành task {}", id);
        return toResponse(taskRepository.save(task));
    }

    /**
     * BR-HK-07: người làm bị gỡ ca tương lai (nghỉ việc, điều chuyển, duyệt đơn nghỉ) thì
     * task đã gán cho họ ở những ngày đó cũng về Chưa phân công, kèm lý do. "Tương lai"
     * theo BR-SCH-17: ngày LỚN HƠN hôm nay — task của chính hôm nay giữ nguyên.
     *
     * @param today mốc "hôm nay" theo múi giờ Location, do nơi gọi tính để ca và task dùng
     *              CHUNG một mốc (xem {@code UserService.terminateUser}).
     */
    @Transactional
    public int releaseFutureTasks(UUID staffId, UnassignedReason reason, LocalDate today) {
        List<HousekeepingTask> tasks = taskRepository.findByAssignedStaffIdAndStatusAndAssignedDateGreaterThan(
            staffId, HousekeepingTaskStatus.IN_PROGRESS, today);

        for (HousekeepingTask task : tasks) {
            requireRoomIntegration(task);   // CHECKOUT: phòng Đang dọn → Chờ dọn (BR-HK-07)
            release(task, reason);
        }
        taskRepository.saveAll(tasks);
        return tasks.size();
    }

    // ── Nội bộ ──────────────────────────────────────────────────────────────

    /**
     * MỌI điều kiện gán task nằm ở đây — BR-HK-02, BR-HK-03, BR-PERM-05. Team đổi quy tắc
     * (ví dụ chỉ cho gán trong ngày) thì chỉ sửa hàm này.
     */
    private void assertCanReceiveTask(UUID staffId, HousekeepingTask task, LocalDate date) {
        if (date.isBefore(ShiftTimeUtils.todayInHanoi())) {
            throw new BusinessException("Không gán task cho ngày đã qua.");
        }

        User staff = userRepository.findByIdAndTenantId(staffId, task.getTenantId())
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", staffId));
        if (!staff.isStaff() || !staff.isActive()) {
            throw new BusinessException("Chỉ gán task cho nhân viên đang làm việc.");
        }
        if (!task.getLocationId().equals(staff.getLocationId())) {
            throw new BusinessException("Nhân viên không thuộc Location của phòng này.");
        }

        boolean housekeeping = positionRepository.findById(staff.getPositionId())
            .map(Position::isHousekeeping)
            .orElse(false);
        if (!housekeeping) {
            throw new BusinessException(
                "Chỉ nhân viên có Position loại Dọn dẹp mới nhận task dọn phòng (BR-PERM-05).");
        }

        // BR-HK-03: có ca trong ngày là đủ, không cần khớp khung giờ. BR-HK-02: không giới
        // hạn số task mỗi người.
        if (!shiftRepository.existsByStaffIdAndShiftDate(staffId, date)) {
            throw new BusinessException(String.format(
                "Nhân viên không có ca làm việc ngày %s — chỉ gán task cho người có ca trong ngày (BR-HK-03).",
                date));
        }
    }

    /**
     * Mọi chỗ gọi hàm này là nơi task CHECKOUT phải đổi trạng thái phòng (BR-ROOM-02) và ghi
     * lịch sử (BR-ROOM-09) — việc của RoomStatusService bên module Quản lý phòng, chưa có.
     * Tới lúc đó chặn rõ ràng thay vì tự đổi trạng thái phòng ở đây: làm vậy sẽ bỏ qua lịch
     * sử phòng và trùng code với module kia.
     */
    private static void requireRoomIntegration(HousekeepingTask task) {
        if (task.getTaskType() == HousekeepingTaskType.CHECKOUT) {
            throw new BusinessException(
                "Task CHECKOUT chưa xử lý được: cần module Quản lý phòng đổi trạng thái phòng "
                + "(BR-ROOM-02). Hiện chỉ hỗ trợ task STAYOVER.");
        }
    }

    /** Task về Chưa phân công — DB ép UNASSIGNED thì không được có người (ck_hk_assignment_pair). */
    private static void release(HousekeepingTask task, UnassignedReason reason) {
        task.setStatus(HousekeepingTaskStatus.UNASSIGNED);
        task.setAssignedStaffId(null);
        task.setAssignedDate(null);
        task.setAssignedBy(null);
        task.setAssignedAt(null);
        task.setUnassignedReason(reason);
    }

    /** Chốt chặn cách ly Tenant: không bao giờ tải task bằng findById trần. */
    private HousekeepingTask getOwnedTask(UUID id) {
        return taskRepository.findByIdAndTenantId(id, SecurityUtils.getCurrentTenantId())
            .orElseThrow(() -> new ResourceNotFoundException("HousekeepingTask", "id", id));
    }

    /** Manager chỉ thao tác trong Location của mình — BR-PERM-03. */
    private void assertManagesLocation(UUID locationId) {
        if (SecurityUtils.hasRole(Role.MANAGER)
                && !locationId.equals(SecurityUtils.getCurrentLocationId())) {
            throw new BusinessException("Không có quyền thao tác trên Location khác.");
        }
    }

    // ── Đọc phòng ───────────────────────────────────────────────────────────
    // Đọc qua EntityManager thay vì RoomRepository: repository đó thuộc module Quản lý phòng
    // (người khác đang làm) — tạo trùng sẽ xung đột khi merge. Có RoomRepository thì thay.

    private Room loadRoom(UUID roomId, UUID tenantId) {
        Room room = entityManager.find(Room.class, roomId);
        // Phòng đã xóa mềm (BR-ROOM-08) coi như không tồn tại.
        if (room == null || !room.isActive() || !tenantId.equals(room.getTenantId())) {
            throw new ResourceNotFoundException("Room", "id", roomId);
        }
        return room;
    }

    private Map<UUID, Room> loadRooms(Collection<HousekeepingTask> tasks) {
        Set<UUID> ids = tasks.stream().map(HousekeepingTask::getRoomId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return entityManager.createQuery("select r from Room r where r.id in :ids", Room.class)
            .setParameter("ids", ids)
            .getResultStream()
            .collect(Collectors.toMap(Room::getId, Function.identity()));
    }

    private HousekeepingTaskResponse toResponse(HousekeepingTask task) {
        return HousekeepingTaskResponse.fromEntity(task, entityManager.find(Room.class, task.getRoomId()));
    }
}
