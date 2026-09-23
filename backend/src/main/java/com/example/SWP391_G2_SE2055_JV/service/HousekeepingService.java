package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.AssignTaskRequest;
import com.example.SWP391_G2_SE2055_JV.dto.AssignableStaffResponse;
import com.example.SWP391_G2_SE2055_JV.dto.CreateStayoverTaskRequest;
import com.example.SWP391_G2_SE2055_JV.dto.HousekeepingTaskResponse;
import com.example.SWP391_G2_SE2055_JV.entity.HousekeepingTask;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.Position;
import com.example.SWP391_G2_SE2055_JV.entity.Room;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.HousekeepingTaskStatus;
import com.example.SWP391_G2_SE2055_JV.enums.HousekeepingTaskType;
import com.example.SWP391_G2_SE2055_JV.enums.PositionType;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import com.example.SWP391_G2_SE2055_JV.enums.TaskCreatedSource;
import com.example.SWP391_G2_SE2055_JV.enums.UnassignedReason;
import com.example.SWP391_G2_SE2055_JV.enums.UserStatus;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.AssignableStaffRepository;
import com.example.SWP391_G2_SE2055_JV.repository.HousekeepingTaskRepository;
import com.example.SWP391_G2_SE2055_JV.repository.LocationRepository;
import com.example.SWP391_G2_SE2055_JV.repository.PositionRepository;
import com.example.SWP391_G2_SE2055_JV.repository.RoomRepository;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftRepository;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import com.example.SWP391_G2_SE2055_JV.utils.ShiftTimeUtils;
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
 * <p><b>Giai đoạn 2 — đã tích hợp module Quản lý phòng</b> (F5, 23/09/2026). Hai loại task
 * đi hai đường khác hẳn nhau, đây là chỗ dễ sai nhất của cả lớp:
 * <ul>
 *   <li><b>STAYOVER</b> — dọn khi khách còn ở: phòng giữ nguyên "Đang sử dụng" suốt vòng đời
 *       task (BR-HK-05). <b>Không bao giờ</b> gọi {@link RoomStatusService}.</li>
 *   <li><b>CHECKOUT</b> — dọn sau khi khách trả phòng: MỖI bước của task kéo theo một bước
 *       chuyển trạng thái phòng (BR-ROOM-02) và một dòng lịch sử (BR-ROOM-09), ủy quyền cho
 *       {@link RoomStatusService} — nơi DUY NHẤT được ghi {@code rooms.status}.</li>
 * </ul>
 *
 * <p>Mọi lời gọi {@code RoomStatusService} nằm trong CÙNG transaction với thay đổi của task:
 * phòng sai trạng thái nguồn (ví dụ Manager vừa khóa phòng) thì task cũng rollback, không bao
 * giờ để task và phòng lệch nhau.
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
    // thay EntityManager bằng RoomRepository đúng như ghi chú cũ ở mục "Đọc phòng" — module
    // Quản lý phòng đã có repository thật, và bản của nó lọc sẵn theo Tenant.
    private final RoomRepository             roomRepository;
    private final LocationRepository         locationRepository;
    private final AssignableStaffRepository  assignableStaffRepository;
    private final RoomStatusService          roomStatusService;

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
            throw new BusinessException("Chỉ tạo task dọn hằng ngày cho phòng đang có khách.");
        }
        if (taskRepository.existsByRoomIdAndTaskTypeAndStatusIn(
                room.getId(), HousekeepingTaskType.STAYOVER, OPEN)) {
            throw new BusinessException("Phòng này đã có task dọn hằng ngày đang mở.");
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

        task.setAssignedStaffId(request.getStaffId());
        task.setAssignedDate(request.getAssignedDate());
        task.setAssignedBy(SecurityUtils.getCurrentUserId());
        task.setAssignedAt(LocalDateTime.now());
        task.setUnassignedReason(null);
        // BR-HK-06: không có trạng thái "đã gán" riêng — gán là bắt đầu thực hiện.
        task.setStatus(HousekeepingTaskStatus.IN_PROGRESS);

        // BR-ROOM-02: gán người = bắt đầu dọn, nên phòng sang "Đang dọn" NGAY, nguồn SYSTEM
        // (không phải người bấm — Manager chỉ phân công, không tự tay đổi trạng thái phòng).
        if (isCheckout(task)) {
            roomStatusService.startCleaning(roomOf(task), task.getId());
        }

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

        release(task, UnassignedReason.MANAGER_MANUAL);
        // Không còn ai dọn thì phòng phải quay lại hàng chờ. Task vẫn MỞ (Chưa phân công) nên
        // không sinh task mới — đó là lý do dùng revertToDirty chứ không đi qua hook (BR-HK-11).
        if (isCheckout(task)) {
            roomStatusService.revertToDirty(roomOf(task), task.getId(),
                releaseReason(UnassignedReason.MANAGER_MANUAL));
        }
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

        if (isCheckout(task)) {
            // BR-HK-06: dọn sau check-out CHƯA xong ở đây — còn chờ Manager nghiệm thu, nên
            // completedAt vẫn để trống (điền khi có kết quả kiểm tra, F6).
            task.setStatus(HousekeepingTaskStatus.PENDING_INSPECTION);
            roomStatusService.markPendingInspection(roomOf(task), task.getId());
        } else {
            // BR-HK-05: dọn hằng ngày xong là xong, khách vẫn đang ở nên KHÔNG đụng tới phòng.
            task.setStatus(HousekeepingTaskStatus.COMPLETED);
            task.setCompletedAt(LocalDateTime.now());
        }

        log.info("Hoàn thành task {} → {}", id, task.getStatus());
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
            release(task, reason);
            if (isCheckout(task)) {
                roomStatusService.revertToDirty(roomOf(task), task.getId(), releaseReason(reason));
            }
        }
        taskRepository.saveAll(tasks);
        return tasks.size();
    }

    /**
     * S-10 — ai gán được việc dọn trong ngày {@code date} (BR-HK-02, BR-HK-03, BR-PERM-05).
     *
     * <p>Chỉ Manager gọi: phạm vi là Location của chính họ, không nhận tham số locationId để
     * không có đường xem nhân sự khách sạn khác. Điều kiện lọc trùng khớp
     * {@link #assertCanReceiveTask} nên mọi người trả về đều gán được.
     */
    @Transactional(readOnly = true)
    public List<AssignableStaffResponse> getAssignableStaff(LocalDate date) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID locationId = SecurityUtils.getCurrentLocationId();

        return assignableStaffRepository
            .findAssignable(tenantId, locationId, date,
                Role.STAFF, UserStatus.ACTIVE, PositionType.HOUSEKEEPING)
            .stream()
            .map(AssignableStaffResponse::fromEntity)
            .toList();
    }

    // ── Nội bộ ──────────────────────────────────────────────────────────────

    /**
     * MỌI điều kiện gán task nằm ở đây — BR-HK-02, BR-HK-03, BR-PERM-05. Team đổi quy tắc
     * (ví dụ chỉ cho gán trong ngày) thì chỉ sửa hàm này.
     */
    private void assertCanReceiveTask(UUID staffId, HousekeepingTask task, LocalDate date) {
        // BR-SCH-17: "hôm nay" tính theo múi giờ của KHÁCH SẠN, không phải giờ máy chủ — phải
        // cùng mốc với lịch làm việc, nếu không thì ca và task lệch nhau một ngày.
        LocalDate today = ShiftTimeUtils.todayAt(locationTimezoneOf(task));
        if (date.isBefore(today)) {
            throw new BusinessException("Không gán task cho ngày đã qua.");
        }
        // Q5 (chốt 22/09/2026): gán task dọn sau check-out là phòng sang "Đang dọn" NGAY. Gán
        // trước cho ngày mai sẽ khiến phòng hiện sai trạng thái suốt hôm nay, nên chặn.
        if (task.getTaskType() == HousekeepingTaskType.CHECKOUT && date.isAfter(today)) {
            throw new BusinessException("Việc dọn sau khi khách trả phòng chỉ gán được cho hôm nay, "
                + "vì phòng chuyển sang «Đang dọn» ngay khi gán người.");
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
                "Chỉ nhân viên có Position loại Dọn dẹp mới nhận task dọn phòng.");
        }

        // BR-HK-03: có ca trong ngày là đủ, không cần khớp khung giờ. BR-HK-02: không giới
        // hạn số task mỗi người.
        if (!shiftRepository.existsByStaffIdAndShiftDate(staffId, date)) {
            throw new BusinessException(String.format(
                "Nhân viên không có ca làm việc ngày %s — chỉ gán task cho người có ca trong ngày.",
                date));
        }
    }

    /**
     * Chỉ task dọn sau check-out mới kéo theo trạng thái phòng — BR-HK-05 vs BR-ROOM-02. Gói
     * thành một hàm để mọi chỗ hỏi cùng một câu hỏi, và để đọc lướt cũng thấy ngay chỗ nào có
     * hệ quả lên phòng.
     */
    private static boolean isCheckout(HousekeepingTask task) {
        return task.getTaskType() == HousekeepingTaskType.CHECKOUT;
    }

    /**
     * Lý do ghi vào LỊCH SỬ PHÒNG khi task bị gỡ người. Người đọc lịch sử phòng không nhìn thấy
     * task, nên câu chữ phải tự nó giải thích được vì sao phòng quay về "Chờ dọn".
     */
    private static String releaseReason(UnassignedReason reason) {
        return switch (reason) {
            case TERMINATION    -> "Người dọn phòng đã nghỉ việc";
            case TRANSFER       -> "Người dọn phòng được điều chuyển";
            case LEAVE_APPROVED -> "Người dọn phòng được duyệt nghỉ";
            case MANAGER_MANUAL -> "Quản lý gỡ người khỏi việc dọn";
        };
    }

    /** Múi giờ của khách sạn chứa phòng — BR-SCH-17. Không tra được coi như dữ liệu ngoài phạm vi. */
    private String locationTimezoneOf(HousekeepingTask task) {
        return locationRepository.findByIdAndTenantId(task.getLocationId(), task.getTenantId())
            .map(Location::getTimezone)
            .orElseThrow(() -> new ResourceNotFoundException("Location", "id", task.getLocationId()));
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

    /**
     * Manager chỉ thao tác trong Location của mình — BR-PERM-03.
     *
     * <p>Q8 (chốt 22/09/2026): trả 404 chứ không phải 403/400. Cùng lý do với cả codebase —
     * 403 là thừa nhận "có tồn tại nhưng bạn không được đụng", tức là lộ dữ liệu của khách sạn
     * khác. {@link #getTask} vốn đã trả 404, giờ ba endpoint ghi cũng vậy cho nhất quán.
     */
    private void assertManagesLocation(UUID locationId) {
        if (SecurityUtils.hasRole(Role.MANAGER)
                && !locationId.equals(SecurityUtils.getCurrentLocationId())) {
            throw new ResourceNotFoundException("Location", "id", locationId);
        }
    }

    // ── Đọc phòng ───────────────────────────────────────────────────────────
    // F5: dùng RoomRepository của module Quản lý phòng. Bản findByIdAndTenantIdAndActiveTrue đã
    // lọc sẵn Tenant và bỏ phòng xóa mềm, nên không phải tự kiểm tra lại như bản EntityManager cũ.

    private Room loadRoom(UUID roomId, UUID tenantId) {
        return roomRepository.findByIdAndTenantIdAndActiveTrue(roomId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Room", "id", roomId));
    }

    /** Phòng của task đang thao tác — dùng cho mọi lời gọi {@link RoomStatusService}. */
    private Room roomOf(HousekeepingTask task) {
        return loadRoom(task.getRoomId(), task.getTenantId());
    }

    /**
     * Tra phòng cho CẢ trang task một lần, tránh N+1. Id lấy từ chính các task đã lọc theo
     * Tenant nên không cần lọc lại; phòng thiếu thì DTO để trống số phòng.
     */
    private Map<UUID, Room> loadRooms(Collection<HousekeepingTask> tasks) {
        Set<UUID> ids = tasks.stream().map(HousekeepingTask::getRoomId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return roomRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(Room::getId, Function.identity()));
    }

    private HousekeepingTaskResponse toResponse(HousekeepingTask task) {
        return HousekeepingTaskResponse.fromEntity(
            task, roomRepository.findByIdAndTenantIdAndActiveTrue(task.getRoomId(), task.getTenantId())
                .orElse(null));
    }
}
