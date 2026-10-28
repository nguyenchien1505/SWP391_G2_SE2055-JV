package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.AssignTaskRequest;
import com.example.SWP391_G2_SE2055_JV.dto.AssignableStaffResponse;
import com.example.SWP391_G2_SE2055_JV.dto.HousekeepingTaskResponse;
import com.example.SWP391_G2_SE2055_JV.dto.InspectTaskRequest;
import com.example.SWP391_G2_SE2055_JV.dto.InspectionRecordResponse;
import com.example.SWP391_G2_SE2055_JV.entity.HousekeepingTask;
import com.example.SWP391_G2_SE2055_JV.entity.InspectionRecord;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.Room;
import com.example.SWP391_G2_SE2055_JV.entity.RoomType;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.HousekeepingTaskStatus;
import com.example.SWP391_G2_SE2055_JV.enums.HousekeepingTaskType;
import com.example.SWP391_G2_SE2055_JV.enums.InspectionResult;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.StaffPermission;
import com.example.SWP391_G2_SE2055_JV.enums.TaskCreatedSource;
import com.example.SWP391_G2_SE2055_JV.enums.UnassignedReason;
import com.example.SWP391_G2_SE2055_JV.enums.UserStatus;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.AssignableStaffRepository;
import com.example.SWP391_G2_SE2055_JV.repository.HousekeepingTaskRepository;
import com.example.SWP391_G2_SE2055_JV.repository.InspectionRecordRepository;
import com.example.SWP391_G2_SE2055_JV.repository.LocationRepository;
import com.example.SWP391_G2_SE2055_JV.repository.RoomRepository;
import com.example.SWP391_G2_SE2055_JV.repository.RoomTypeRepository;
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
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lịch dọn phòng — BR-HK-01..12, DM-04, DM-05.
 *
 * <p>Chỉ còn việc dọn sau khi khách trả phòng (CHECKOUT): việc dọn hằng ngày đã bỏ (V4, chốt
 * 05/10/2026, thay BR-HK-05/10). MỖI bước của việc dọn kéo theo một bước chuyển trạng thái phòng
 * (BR-ROOM-02) và một dòng lịch sử (BR-ROOM-09), ủy quyền cho {@link RoomStatusService} — nơi DUY
 * NHẤT được ghi {@code rooms.status}.
 *
 * <p>Một phòng có thể NHIỀU người cùng dọn (thay DM-04 "một task một người"): giao lần đầu là cả
 * nhóm bắt đầu dọn và phòng sang «Đang dọn»; giao thêm là thêm người vào nhóm; bất kỳ ai trong nhóm
 * bấm "Hoàn thành" là xong cho cả nhóm. Gỡ đến người cuối cùng thì việc quay về hàng chờ và phòng
 * về «Chờ dọn».
 *
 * <p>Mọi lời gọi {@code RoomStatusService} nằm trong CÙNG transaction với thay đổi của task:
 * phòng sai trạng thái nguồn (ví dụ Manager vừa khóa phòng) thì task cũng rollback, không bao
 * giờ để task và phòng lệch nhau.
 *
 * <p>Giám sát vận hành (BR-PERM-07, chốt 19/10/2026) làm thay Manager phần giao việc và kiểm tra
 * phòng trong Location của mình, chỉ trong ngày có ca ({@link SupervisorDutyRule}); gỡ người khỏi
 * việc dọn vẫn chỉ Manager.
 *
 * <p>Phản ứng khi PHÒNG đổi trạng thái (sinh/hủy task) nằm ở {@link HousekeepingRoomHooks}.
 * Ngoại lệ DUY NHẤT đi ngược chiều đó là bước nghiệm thu không đạt (F6): lớp này tự sinh việc
 * dọn lại để giữ đúng nguồn {@code INSPECTION_FAILED} — xem {@code createFollowUpTask}.
 *
 * <p>Mọi truy vấn đều lọc theo tenantId lấy từ session — không dùng {@code findAll()} trần.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HousekeepingService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Số phòng theo thứ tự tự nhiên: 2 trước 10, "G01" cạnh "G02". */
    private static final Comparator<String> ROOM_NUMBER_ORDER =
        Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder());

    private final HousekeepingTaskRepository taskRepository;
    private final UserRepository             userRepository;
    private final ShiftRepository            shiftRepository;
    // thay EntityManager bằng RoomRepository đúng như ghi chú cũ ở mục "Đọc phòng" — module
    // Quản lý phòng đã có repository thật, và bản của nó lọc sẵn theo Tenant.
    private final RoomRepository             roomRepository;
    private final RoomTypeRepository         roomTypeRepository;
    private final LocationRepository         locationRepository;
    private final AssignableStaffRepository  assignableStaffRepository;
    private final InspectionRecordRepository inspectionRepository;
    private final RoomStatusService          roomStatusService;
    private final SupervisorDutyRule         supervisorDutyRule;
    private final CleaningWorkDayRule        cleaningWorkDayRule;

    /**
     * Lịch dọn. Nhân viên chỉ thấy việc có mình trong nhóm dọn (BR-PERM-05); Manager chỉ thấy
     * task trong Location của mình; Giám đốc thấy toàn Tenant.
     *
     * @param from ngày làm nhỏ nhất (tính cả ngày này), đi cùng {@code to} — lịch dọn theo tuần.
     *             Bỏ trống cả hai thì không lọc theo khoảng ngày.
     * @param to   ngày làm lớn nhất (tính cả ngày này)
     * @param locationWide Giám sát vận hành xem cả lịch dọn của khách sạn mình — bảng điều phối
     *             (BR-PERM-07); người khác bỏ qua cờ này. Không bật thì Giám sát kiêm Dọn dẹp vẫn chỉ
     *             thấy việc của mình, như mọi nhân viên (màn «Việc của tôi»).
     */
    @Transactional(readOnly = true)
    public Page<HousekeepingTaskResponse> getTasks(HousekeepingTaskStatus status,
                                                   HousekeepingTaskType taskType,
                                                   LocalDate assignedDate,
                                                   LocalDate from,
                                                   LocalDate to,
                                                   boolean locationWide,
                                                   Pageable pageable) {
        if ((from == null) != (to == null)) {
            throw new BusinessException("Lọc theo khoảng ngày cần đủ cả from và to.");
        }
        if (from != null && to.isBefore(from)) {
            throw new BusinessException("Ngày kết thúc (to) không được trước ngày bắt đầu (from).");
        }
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID locationId = null;
        UUID staffId = null;
        if (SecurityUtils.hasRole(Role.MANAGER) || (locationWide && SecurityUtils.isSupervisor())) {
            locationId = SecurityUtils.getCurrentLocationId();
        } else if (SecurityUtils.hasRole(Role.STAFF)) {
            staffId = SecurityUtils.getCurrentUserId();
        }

        Page<HousekeepingTask> page = taskRepository.search(
            tenantId, locationId, staffId, status, taskType, assignedDate, from, to, pageable);
        ResponseLookups lookups = loadLookups(page.getContent());
        return page.map(lookups::toResponse);
    }

    /** Cùng phạm vi với {@link #getTasks}; ngoài phạm vi trả 404 để không lộ task có tồn tại. */
    @Transactional(readOnly = true)
    public HousekeepingTaskResponse getTask(UUID id) {
        HousekeepingTask task = getOwnedTask(id);
        assertCanView(task);
        return toResponse(task);
    }

    /**
     * BR-HK-02: Manager — hoặc Giám sát vận hành (BR-PERM-07) — giao tay, không có gợi ý tự động phân
     * bổ. Một lần giao được nhiều người.
     *
     * <ul>
     *   <li>Việc đang <b>chưa phân công</b>: nhóm này bắt đầu dọn — việc sang «Đang thực hiện» và
     *       phòng sang «Đang dọn» ngay (BR-ROOM-02, Q5).</li>
     *   <li>Việc đang <b>thực hiện</b>: thêm những người này vào nhóm. Cả nhóm dọn cùng một ngày,
     *       nên ngày giao phải đúng ngày của việc đó.</li>
     * </ul>
     * Mỗi người đều phải qua đủ điều kiện nhận việc ({@link #assertCanReceiveTask}).
     */
    @Transactional
    public HousekeepingTaskResponse assignTask(UUID id, AssignTaskRequest request) {
        HousekeepingTask task = getOwnedTask(id);
        assertCanCoordinate(task.getLocationId());

        Set<UUID> requested = new LinkedHashSet<>(request.getStaffIds());
        LocalDate date = request.getAssignedDate();

        if (task.getStatus() == HousekeepingTaskStatus.UNASSIGNED) {
            for (UUID staffId : requested) {
                assertCanReceiveTask(staffId, task, date);
            }
            task.getAssigneeIds().clear();
            task.getAssigneeIds().addAll(requested);
            task.setAssignedDate(date);
            task.setAssignedBy(SecurityUtils.getCurrentUserId());
            task.setAssignedAt(LocalDateTime.now());
            task.setUnassignedReason(null);
            // BR-HK-06: không có trạng thái "đã gán" riêng — gán là bắt đầu thực hiện.
            task.setStatus(HousekeepingTaskStatus.IN_PROGRESS);
            // BR-ROOM-02: gán người = bắt đầu dọn, nên phòng sang "Đang dọn" NGAY, nguồn SYSTEM
            // (không phải người bấm — Manager chỉ phân công, không tự tay đổi trạng thái phòng).
            roomStatusService.startCleaning(roomOf(task), task.getId());
            log.info("Giao task {} cho {} người ngày {}", id, requested.size(), date);
        } else if (task.getStatus() == HousekeepingTaskStatus.IN_PROGRESS) {
            if (!date.equals(task.getAssignedDate())) {
                throw new BusinessException(String.format(
                    "Việc dọn này đang làm ngày %s — người thêm vào phải dọn cùng ngày đó.",
                    task.getAssignedDate().format(DAY)));
            }
            requested.removeAll(task.getAssigneeIds());
            if (requested.isEmpty()) {
                throw new BusinessException("Những người này đã có trong nhóm dọn phòng này.");
            }
            for (UUID staffId : requested) {
                assertCanReceiveTask(staffId, task, date);
            }
            task.getAssigneeIds().addAll(requested);
            log.info("Thêm {} người vào task {}", requested.size(), id);
        } else {
            throw new BusinessException(task.isOpen()
                ? "Việc dọn đã báo xong, đang chờ kiểm tra — không thêm người được nữa."
                : "Task đã đóng, không gán người được nữa.");
        }

        return toResponse(taskRepository.save(task));
    }

    /**
     * Manager gỡ người khỏi việc đang thực hiện — BR-PERM-03 "điều chỉnh lịch dọn".
     *
     * @param staffId người cần gỡ; {@code null} = gỡ cả nhóm. Gỡ đến người cuối cùng thì việc về
     *                Chưa phân công với lý do MANAGER_MANUAL (cùng bộ lý do với ca — BR-SCH-24) và
     *                phòng về «Chờ dọn».
     */
    @Transactional
    public HousekeepingTaskResponse unassignTask(UUID id, UUID staffId) {
        HousekeepingTask task = getOwnedTask(id);
        assertManagesLocation(task.getLocationId());

        if (task.getStatus() != HousekeepingTaskStatus.IN_PROGRESS) {
            throw new BusinessException("Chỉ gỡ người khỏi task đang thực hiện.");
        }
        if (staffId != null) {
            if (!task.isAssignedTo(staffId)) {
                throw new BusinessException("Người này không có trong nhóm dọn phòng này.");
            }
            task.getAssigneeIds().remove(staffId);
        }

        if (staffId == null || !task.isAssigned()) {
            release(task, UnassignedReason.MANAGER_MANUAL);
            // Không còn ai dọn thì phòng phải quay lại hàng chờ. Task vẫn MỞ (Chưa phân công) nên
            // không sinh task mới — đó là lý do dùng revertToDirty chứ không đi qua hook (BR-HK-11).
            roomStatusService.revertToDirty(roomOf(task), task.getId(),
                releaseReason(UnassignedReason.MANAGER_MANUAL));
            log.info("Gỡ cả nhóm khỏi task {}", id);
        } else {
            log.info("Gỡ staff {} khỏi task {} — còn {} người", staffId, id, task.getAssigneeIds().size());
        }
        return toResponse(taskRepository.save(task));
    }

    /**
     * Người trong nhóm dọn bấm hoàn thành — BR-PERM-05, BR-ROOM-02. Một người bấm là xong cho cả
     * nhóm (chốt 05/10/2026): việc sang «Chờ kiểm tra», chưa phải hoàn thành.
     */
    @Transactional
    public HousekeepingTaskResponse completeTask(UUID id) {
        HousekeepingTask task = getOwnedTask(id);

        if (!task.isAssignedTo(SecurityUtils.getCurrentUserId())) {
            throw new BusinessException("Chỉ người trong nhóm dọn mới bấm hoàn thành task này.");
        }
        if (task.getStatus() != HousekeepingTaskStatus.IN_PROGRESS) {
            throw new BusinessException("Task không ở trạng thái đang thực hiện.");
        }

        // BR-HK-06: việc dọn CHƯA xong ở đây — còn chờ Manager nghiệm thu, nên completedAt vẫn để
        // trống (điền khi có kết quả kiểm tra, F6).
        task.setStatus(HousekeepingTaskStatus.PENDING_INSPECTION);
        roomStatusService.markPendingInspection(roomOf(task), task.getId());

        log.info("Hoàn thành task {} → {}", id, task.getStatus());
        return toResponse(taskRepository.save(task));
    }

    /**
     * BR-HK-07: người làm bị gỡ ca tương lai (nghỉ việc, điều chuyển, duyệt đơn nghỉ) thì cũng bị gỡ
     * khỏi nhóm dọn của những ngày đó, kèm lý do. Nhóm còn người khác thì việc vẫn tiếp tục; hết
     * người thì việc về Chưa phân công và phòng về «Chờ dọn». "Tương lai" theo BR-SCH-17: ngày LỚN
     * HƠN hôm nay — việc của chính hôm nay giữ nguyên.
     *
     * @param today mốc "hôm nay" theo múi giờ Location, do nơi gọi tính để ca và task dùng
     *              CHUNG một mốc (xem {@code UserService.terminateUser}).
     * @return số việc người này bị gỡ khỏi
     */
    @Transactional
    public int releaseFutureTasks(UUID staffId, UnassignedReason reason, LocalDate today) {
        List<HousekeepingTask> tasks =
            taskRepository.findOfStaffAfter(staffId, HousekeepingTaskStatus.IN_PROGRESS, today);

        for (HousekeepingTask task : tasks) {
            task.getAssigneeIds().remove(staffId);
            if (!task.isAssigned()) {
                release(task, reason);
                roomStatusService.revertToDirty(roomOf(task), task.getId(), releaseReason(reason));
            }
        }
        taskRepository.saveAll(tasks);
        return tasks.size();
    }

    /**
     * BR-HK-03 từ phía lịch làm việc: gỡ người khỏi ca, xóa ca hay dời ca sang ngày khác không
     * được làm một người MẤT ngày làm việc trong khi họ còn việc dọn đang làm hôm đó — nếu không,
     * việc sẽ nằm trên tên một người không có ca. Nhóm đã chốt: CHẶN và chỉ rõ phòng để Manager
     * gỡ người khỏi các việc đó trước, không tự gỡ (khác BR-HK-07 vốn chỉ áp cho gỡ ca tự động).
     *
     * <p>Việc «Chờ kiểm tra» không tính: người dọn đã xong phần mình, phần còn lại là của
     * Manager. {@code ShiftService} gọi hàm này TRƯỚC khi đổi ca.
     *
     * @param leavingShiftId ca đang bị gỡ / xóa / dời — ca khác cùng ngày vẫn giữ ngày làm việc
     */
    @Transactional(readOnly = true)
    public void assertCanLeaveShiftDay(UUID staffId, LocalDate date, UUID leavingShiftId) {
        if (shiftRepository.existsByStaffIdAndShiftDateAndIdNot(staffId, date, leavingShiftId)) {
            return;
        }
        List<HousekeepingTask> tasks =
            taskRepository.findOfStaffOn(staffId, date, HousekeepingTaskStatus.IN_PROGRESS);
        if (tasks.isEmpty()) {
            return;
        }

        Map<UUID, Room> rooms = loadRooms(tasks);
        String roomNumbers = tasks.stream()
            .map(task -> rooms.get(task.getRoomId()))
            .filter(Objects::nonNull)
            .map(Room::getRoomNumber)
            .sorted(ROOM_NUMBER_ORDER)
            .collect(Collectors.joining(", "));
        String staffName = userRepository.findByIdAndTenantId(staffId, tasks.get(0).getTenantId())
            .map(User::getFullName)
            .orElse("Nhân viên này");
        throw new BusinessException(String.format(
            "%s đang giữ %d việc dọn ngày %s (phòng %s). Gỡ người khỏi các việc đó ở màn "
                + "«Công việc dọn phòng» trước khi gỡ, xóa hoặc dời ca này.",
            staffName, tasks.size(), date.format(DAY), roomNumbers));
    }

    /**
     * Số việc dọn nhân viên đang làm dở (có mình trong nhóm, chưa bấm hoàn thành) — ở mọi ngày. Chỉ
     * đếm «Đang thực hiện»: việc «Chờ kiểm tra» người dọn đã làm xong phần mình, còn lại là
     * việc của Manager.
     */
    @Transactional(readOnly = true)
    public long countInProgressTasksOf(UUID staffId) {
        return taskRepository.countOfStaff(staffId, HousekeepingTaskStatus.IN_PROGRESS);
    }

    /**
     * S-10 — ai gán được việc dọn trong ngày {@code date} (BR-HK-02, BR-HK-03, BR-PERM-05).
     *
     * <p>Chỉ Manager và Giám sát vận hành gọi: phạm vi là Location của chính họ, không nhận tham số
     * locationId để không có đường xem nhân sự khách sạn khác.
     *
     * @param ongoingOnly hộp phân công bật cờ này để chỉ nhận người gán ĐƯỢC NGAY, trùng khớp
     *                    {@link #assertCanReceiveTask}: hôm nay là người có ca hôm nay, hôm qua là người
     *                    đang làm dở ca qua đêm bắt đầu hôm qua (BR-SCH-03), ngày khác thì rỗng. Không bật
     *                    thì trả mọi người có ca ngày đó — bảng "Theo nhân viên" dựng hàng cho cả ngày đã
     *                    qua / tương lai để xem.
     */
    @Transactional(readOnly = true)
    public List<AssignableStaffResponse> getAssignableStaff(LocalDate date, boolean ongoingOnly) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID locationId = SecurityUtils.getCurrentLocationId();

        List<User> staff = ongoingOnly
            ? findAssignableNow(tenantId, locationId, date)
            : findAssignable(tenantId, locationId, date);
        return staff.stream().map(AssignableStaffResponse::fromEntity).toList();
    }

    /**
     * S-13 — Manager hoặc Giám sát vận hành nghiệm thu phòng sau khi nhân viên báo dọn xong
     * (BR-HK-06, BR-HK-08, BR-HK-12, BR-PERM-07). Chỉ task đang «Chờ kiểm tra» mới đi qua bước này.
     *
     * <p>ĐẠT hay KHÔNG ĐẠT thì task gốc đều sang {@code COMPLETED} — không có trạng thái FAILED
     * (BR-HK-06). Kết quả nằm ở {@link InspectionRecord}; không đạt thì sinh thêm việc dọn lại
     * trỏ ngược về task gốc (BR-HK-12) và phòng quay về «Chờ dọn».
     */
    @Transactional
    public InspectionRecordResponse inspectTask(UUID id, InspectTaskRequest request) {
        HousekeepingTask task = getOwnedTask(id);
        assertCanCoordinate(task.getLocationId());
        if (task.getStatus() != HousekeepingTaskStatus.PENDING_INSPECTION) {
            // BR-HK-06: nghiệm thu đúng MỘT lần — kiểm xong task sang COMPLETED nên không quay lại.
            throw new BusinessException("Chỉ kiểm tra được việc dọn đang chờ kiểm tra.");
        }
        // BR-HK-13: Giám sát kiêm Dọn dẹp không tự nghiệm thu phòng mình vừa dọn.
        if (SecurityUtils.isSupervisor() && task.isAssignedTo(SecurityUtils.getCurrentUserId())) {
            throw new BusinessException("Bạn nằm trong nhóm dọn phòng này nên không tự kiểm tra được — "
                + "nhờ Quản lý hoặc một Giám sát khác kiểm tra.");
        }

        boolean failed = request.getResult() == InspectionResult.FAIL;
        String reason = normalize(request.getReason());
        if (failed && reason == null) {
            throw new BusinessException("Kiểm tra không đạt thì bắt buộc nhập lý do.");
        }
        Room room = roomOf(task);

        task.setStatus(HousekeepingTaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now());
        taskRepository.saveAndFlush(task);          // BẮT BUỘC — xem javadoc createFollowUpTask

        HousekeepingTask next = failed ? createFollowUpTask(task) : null;
        roomStatusService.applyInspection(room, request.getResult(), reason, task.getId());

        log.info("Kiểm tra phòng {} sau task {}: {}", room.getRoomNumber(), id, request.getResult());
        return InspectionRecordResponse.fromEntity(
            inspectionRepository.save(inspectionRecord(task, request.getResult(), reason, next)));
    }

    /**
     * Biên bản kiểm tra của một task — dùng để hiện lý do «không đạt» trên thẻ việc dọn lại
     * (BR-HK-12). Cùng phạm vi xem với {@link #getTask}.
     */
    @Transactional(readOnly = true)
    public InspectionRecordResponse getInspection(UUID taskId) {
        HousekeepingTask task = getOwnedTask(taskId);
        assertCanView(task);

        return inspectionRepository.findByTenantIdAndTaskId(task.getTenantId(), taskId)
            .map(InspectionRecordResponse::fromEntity)
            .orElseThrow(() -> new ResourceNotFoundException("InspectionRecord", "taskId", taskId));
    }

    // ── Nội bộ ──────────────────────────────────────────────────────────────

    /**
     * MỌI điều kiện để MỘT người nhận việc nằm ở đây — BR-HK-02, BR-HK-03, BR-PERM-05. Team đổi quy
     * tắc thì chỉ sửa hàm này; riêng "ngày làm nào giao được" (hôm nay, hoặc ca qua đêm bắt đầu hôm qua
     * còn đang làm) nằm ở {@link CleaningWorkDayRule#assertIsCurrentWorkDay}.
     */
    private void assertCanReceiveTask(UUID staffId, HousekeepingTask task, LocalDate date) {
        User staff = userRepository.findByIdAndTenantId(staffId, task.getTenantId())
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", staffId));
        if (!staff.isStaff() || !staff.isActive()) {
            throw new BusinessException("Chỉ gán task cho nhân viên đang làm việc.");
        }
        if (!task.getLocationId().equals(staff.getLocationId())) {
            throw new BusinessException("Nhân viên không thuộc Location của phòng này.");
        }

        // Quyền Dọn dẹp do Manager tick cho từng người (có thể kiêm Lễ tân).
        if (!staff.getPermissions().contains(StaffPermission.HOUSEKEEPING)) {
            throw new BusinessException(staff.getFullName()
                + " chưa được cấp quyền Dọn dẹp nên không nhận task dọn phòng.");
        }

        // BR-HK-03: có ca trong ngày làm là đủ, không cần khớp khung giờ. BR-HK-02: không giới hạn số
        // task mỗi người. BR-SCH-17: "bây giờ" theo múi giờ của KHÁCH SẠN, không phải giờ máy chủ —
        // phải cùng mốc với lịch làm việc, nếu không ca và task lệch nhau một ngày.
        LocalDateTime now = ShiftTimeUtils.nowAt(locationTimezoneOf(task));
        cleaningWorkDayRule.assertIsCurrentWorkDay(staff, task.getLocationId(), date, now);
    }

    /**
     * Ai được XEM một task — BR-PERM-05. Nhân viên chỉ thấy việc có mình trong nhóm dọn, Manager và
     * Giám sát vận hành (BR-PERM-07) thấy task trong Location của mình, Giám đốc thấy cả Tenant. Ngoài
     * phạm vi trả 404 để không lộ task có tồn tại. Dùng chung cho {@link #getTask} và
     * {@link #getInspection}.
     */
    private void assertCanView(HousekeepingTask task) {
        boolean locationScoped = SecurityUtils.hasRole(Role.MANAGER) || SecurityUtils.isSupervisor();
        boolean outOfScope =
            (SecurityUtils.hasRole(Role.STAFF) && !locationScoped
                && !task.isAssignedTo(SecurityUtils.getCurrentUserId()))
            || (locationScoped
                && !SecurityUtils.getCurrentLocationId().equals(task.getLocationId()));
        if (outOfScope) {
            throw new ResourceNotFoundException("HousekeepingTask", "id", task.getId());
        }
    }

    /**
     * BR-HK-12: kiểm tra không đạt thì sinh việc dọn lại, trỏ ngược về task gốc qua
     * {@code parentTaskId}. KHÔNG gọi {@code HousekeepingRoomHooks.onRoomBecameDirty}: hook đó
     * sinh task nguồn {@code CHECKOUT_AUTO}, không phân biệt được với lần dọn đầu.
     *
     * <p>⚠️ Task gốc phải được {@code saveAndFlush} TRƯỚC khi gọi hàm này. Hai task cùng khóa
     * {@code room:CHECKOUT} của unique {@code uk_hk_open_task_per_room_type} (BR-HK-11); id sinh
     * kiểu UUID nên {@code save} chưa INSERT ngay, và khi flush thì Hibernate chạy INSERT trước
     * UPDATE — task mới sẽ chào đời trong lúc task gốc vẫn đang mở → 409.
     */
    private HousekeepingTask createFollowUpTask(HousekeepingTask origin) {
        return taskRepository.save(HousekeepingTask.builder()
            .tenantId(origin.getTenantId())
            .locationId(origin.getLocationId())
            .roomId(origin.getRoomId())
            .taskType(HousekeepingTaskType.CHECKOUT)
            .status(HousekeepingTaskStatus.UNASSIGNED)
            .createdSource(TaskCreatedSource.INSPECTION_FAILED)
            .parentTaskId(origin.getId())
            .build());
    }

    /** Biên bản nghiệm thu — người kiểm tra luôn là người đang đăng nhập (BR-ROOM-02). */
    private static InspectionRecord inspectionRecord(HousekeepingTask task, InspectionResult result,
                                                     String reason, HousekeepingTask next) {
        return InspectionRecord.builder()
            .tenantId(task.getTenantId())
            .taskId(task.getId())
            .roomId(task.getRoomId())
            .inspectorId(SecurityUtils.getCurrentUserId())
            .result(result)
            .reason(reason)
            .inspectedAt(LocalDateTime.now())
            .nextTaskId(next == null ? null : next.getId())
            .build();
    }

    /** Ô lý do để trống hoặc toàn khoảng trắng coi như không nhập. */
    private static String normalize(String reason) {
        return StringUtils.hasText(reason) ? reason.trim() : null;
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

    /** Múi giờ của khách sạn chứa phòng — BR-SCH-17. */
    private String locationTimezoneOf(HousekeepingTask task) {
        return locationTimezone(task.getLocationId(), task.getTenantId());
    }

    /** Múi giờ của một khách sạn — BR-SCH-17. Không tra được coi như dữ liệu ngoài phạm vi. */
    private String locationTimezone(UUID locationId, UUID tenantId) {
        return locationRepository.findByIdAndTenantId(locationId, tenantId)
            .map(Location::getTimezone)
            .orElseThrow(() -> new ResourceNotFoundException("Location", "id", locationId));
    }

    /**
     * Người gán được NGAY vào ngày {@code date} — cùng luật {@link CleaningWorkDayRule#assertIsCurrentWorkDay}:
     * hôm nay → có ca hôm nay; hôm qua → đang làm dở ca qua đêm bắt đầu hôm qua; ngày khác → không ai.
     */
    private List<User> findAssignableNow(UUID tenantId, UUID locationId, LocalDate date) {
        LocalDateTime now = ShiftTimeUtils.nowAt(locationTimezone(locationId, tenantId));
        LocalDate today = now.toLocalDate();
        if (date.equals(today)) {
            return findAssignable(tenantId, locationId, date);
        }
        if (date.equals(today.minusDays(1))) {
            return assignableStaffRepository.findOnOvernightShift(tenantId, locationId, date, now.toLocalTime(),
                Role.STAFF, UserStatus.ACTIVE, StaffPermission.HOUSEKEEPING);
        }
        return List.of();
    }

    /** Mọi nhân viên dọn phòng có ca ngày {@code date} tại khách sạn này (BR-HK-03, BR-PERM-05). */
    private List<User> findAssignable(UUID tenantId, UUID locationId, LocalDate date) {
        return assignableStaffRepository.findAssignable(tenantId, locationId, date,
            Role.STAFF, UserStatus.ACTIVE, StaffPermission.HOUSEKEEPING);
    }

    /** Task về Chưa phân công — không còn ai trong nhóm, không còn ngày làm. */
    private static void release(HousekeepingTask task, UnassignedReason reason) {
        task.setStatus(HousekeepingTaskStatus.UNASSIGNED);
        task.getAssigneeIds().clear();
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
     * khác. {@link #getTask} vốn đã trả 404, giờ các endpoint ghi cũng vậy cho nhất quán.
     */
    private void assertManagesLocation(UUID locationId) {
        if (SecurityUtils.hasRole(Role.MANAGER)
                && !locationId.equals(SecurityUtils.getCurrentLocationId())) {
            throw new ResourceNotFoundException("Location", "id", locationId);
        }
    }

    /**
     * Giao việc và kiểm tra phòng: Manager như {@link #assertManagesLocation}; Giám sát vận hành cũng
     * chỉ trong Location của mình (ngoài phạm vi trả 404, cùng lý do Q8) và chỉ trong ngày có ca
     * (BR-PERM-07). Gỡ người không đi qua đây — việc đó chỉ Manager làm.
     */
    private void assertCanCoordinate(UUID locationId) {
        if (!SecurityUtils.isSupervisor()) {
            assertManagesLocation(locationId);
            return;
        }
        if (!locationId.equals(SecurityUtils.getCurrentLocationId())) {
            throw new ResourceNotFoundException("Location", "id", locationId);
        }
        supervisorDutyRule.assertOnDuty();
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

    // ── Dựng response ───────────────────────────────────────────────────────

    private HousekeepingTaskResponse toResponse(HousekeepingTask task) {
        return loadLookups(List.of(task)).toResponse(task);
    }

    /**
     * Phòng, loại phòng, tên người dọn và cờ «Tồn đọng» cho CẢ một trang task, mỗi loại một câu truy
     * vấn. Mọi id lấy từ chính các task đã lọc theo Tenant nên không cần lọc lại.
     *
     * <p>BR-HK-04: việc nào tồn đọng do backend quyết định ({@link CleaningWorkDayRule#overdueTaskIds}) —
     * FE không tự so ngày, vì việc của ca qua đêm bắt đầu hôm qua chưa tồn đọng khi ca chưa hết.
     */
    private ResponseLookups loadLookups(Collection<HousekeepingTask> tasks) {
        Map<UUID, Room> rooms = loadRooms(tasks);

        Set<UUID> typeIds = rooms.values().stream().map(Room::getRoomTypeId).collect(Collectors.toSet());
        Map<UUID, String> typeNames = typeIds.isEmpty() ? Map.of()
            : roomTypeRepository.findAllById(typeIds).stream()
                .collect(Collectors.toMap(RoomType::getId, RoomType::getName));

        Set<UUID> staffIds = tasks.stream()
            .flatMap(task -> task.getAssigneeIds().stream())
            .collect(Collectors.toSet());
        Map<UUID, String> staffNames = staffIds.isEmpty() ? Map.of()
            : userRepository.findAllById(staffIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));

        return new ResponseLookups(rooms, typeNames, staffNames, cleaningWorkDayRule.overdueTaskIds(tasks));
    }

    private record ResponseLookups(Map<UUID, Room> rooms, Map<UUID, String> typeNames,
                                   Map<UUID, String> staffNames, Set<UUID> overdueTaskIds) {

        HousekeepingTaskResponse toResponse(HousekeepingTask task) {
            Room room = rooms.get(task.getRoomId());
            return HousekeepingTaskResponse.fromEntity(
                task, room, room == null ? null : typeNames.get(room.getRoomTypeId()), staffNames,
                overdueTaskIds.contains(task.getId()));
        }
    }
}
