package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.AssignTaskRequest;
import com.example.SWP391_G2_SE2055_JV.dto.AssignableStaffResponse;
import com.example.SWP391_G2_SE2055_JV.dto.HousekeepingTaskResponse;
import com.example.SWP391_G2_SE2055_JV.dto.InspectTaskRequest;
import com.example.SWP391_G2_SE2055_JV.dto.InspectionRecordResponse;
import com.example.SWP391_G2_SE2055_JV.dto.OpenTasksResponse;
import com.example.SWP391_G2_SE2055_JV.dto.ReturnTaskRequest;
import com.example.SWP391_G2_SE2055_JV.dto.TaskReturnResponse;
import com.example.SWP391_G2_SE2055_JV.entity.HousekeepingTask;
import com.example.SWP391_G2_SE2055_JV.entity.HousekeepingTaskReturn;
import com.example.SWP391_G2_SE2055_JV.entity.InspectionRecord;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.Room;
import com.example.SWP391_G2_SE2055_JV.entity.RoomType;
import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.HousekeepingTaskStatus;
import com.example.SWP391_G2_SE2055_JV.enums.HousekeepingTaskType;
import com.example.SWP391_G2_SE2055_JV.enums.InspectionResult;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.StaffPermission;
import com.example.SWP391_G2_SE2055_JV.enums.TaskAssignmentSource;
import com.example.SWP391_G2_SE2055_JV.enums.TaskCreatedSource;
import com.example.SWP391_G2_SE2055_JV.enums.UnassignedReason;
import com.example.SWP391_G2_SE2055_JV.enums.UserStatus;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.AssignableStaffRepository;
import com.example.SWP391_G2_SE2055_JV.repository.HousekeepingTaskRepository;
import com.example.SWP391_G2_SE2055_JV.repository.HousekeepingTaskReturnRepository;
import com.example.SWP391_G2_SE2055_JV.repository.HousekeepingTaskReturnRepository.TaskReturnCount;
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
import org.springframework.transaction.annotation.Isolation;
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
import java.util.Optional;
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
 * <p>Open-task (M2): nhân viên Dọn dẹp tự nhận việc chưa phân công ({@link #claimTask}) và trả lại việc
 * mình đã tự nhận ({@link #returnTask}).
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
    private final HousekeepingTaskReturnRepository returnRepository;
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
     * Mỗi người đều phải qua đủ điều kiện nhận việc ({@link #assertCanReceiveTask}). Cách giao lưu là
     * Manager gán hay Giám sát gán (BR-HK-12 sửa M2); thêm người vào nhóm không đổi cách giao.
     */
    @Transactional
    public HousekeepingTaskResponse assignTask(UUID id, AssignTaskRequest request) {
        HousekeepingTask task = getOwnedTaskForUpdate(id);
        assertCanCoordinate(task.getLocationId());

        Set<UUID> requested = new LinkedHashSet<>(request.getStaffIds());
        LocalDate date = request.getAssignedDate();

        if (task.getStatus() == HousekeepingTaskStatus.UNASSIGNED) {
            for (UUID staffId : requested) {
                assertCanReceiveTask(staffId, task, date);
            }
            startTask(task, requested, date, coordinatorSource());
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
        HousekeepingTask task = getOwnedTaskForUpdate(id);
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
        HousekeepingTask task = getOwnedTaskForUpdate(id);

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
     * S-21 — việc đang chờ nhận của khách sạn mình (BR-HK-14): MỌI việc «Chưa phân công», kể cả việc dọn
     * lại; Quản lý không phải "mở" từng việc. Kèm luôn "bạn có tự nhận được không, vì sao" để màn hình
     * không tự suy luật (BR-HK-15, BR-HK-16). Chưa nhận được thì danh sách vẫn trả về.
     */
    @Transactional(readOnly = true)
    public OpenTasksResponse getOpenTasks() {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID locationId = SecurityUtils.getCurrentLocationId();
        List<HousekeepingTask> open = taskRepository.search(tenantId, locationId, null,
            HousekeepingTaskStatus.UNASSIGNED, null, null, null, null, Pageable.unpaged()).getContent();

        LocalDateTime now = ShiftTimeUtils.nowAt(locationTimezone(locationId, tenantId));
        ClaimCheck check = checkClaim(SecurityUtils.getCurrentUserId(), locationId, now, false);
        ResponseLookups lookups = loadLookups(open);
        return OpenTasksResponse.builder()
            .canClaim(check.allowed())
            .blockedReason(check.reason())
            .workDay(check.workDay())
            .tasks(open.stream().map(lookups::toResponse).toList())
            .build();
    }

    /**
     * RM-21 — nhân viên Dọn dẹp tự nhận một việc chưa phân công của khách sạn mình (BR-HK-02 bản sửa,
     * BR-HK-14 → 16, BR-HK-18). Nhận = giao: việc sang «Đang thực hiện», phòng sang «Đang dọn» ngay
     * (BR-ROOM-02 bản sửa), cách giao là tự nhận (BR-HK-12 bản sửa). Ngày làm là ngày của ca đang làm.
     *
     * <p>READ_COMMITTED + khóa dòng theo thứ tự việc → ca của mình: hai người cùng bấm thì người sau đọc
     * được việc ĐÃ có người; một người bấm hai việc cùng lúc thì lần sau đếm được việc vừa nhận. Ở mức
     * REPEATABLE READ mặc định, câu đếm của lần sau đọc theo ảnh chụp cũ và vẫn ra 0.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public HousekeepingTaskResponse claimTask(UUID id) {
        HousekeepingTask task = getOwnedTaskForUpdate(id);                     // ① khóa việc — câu ĐẦU TIÊN
        if (!task.getLocationId().equals(SecurityUtils.getCurrentLocationId())) {
            throw new ResourceNotFoundException("HousekeepingTask", "id", id);  // Q8: không lộ việc nơi khác
        }
        Room room = roomOf(task);
        assertStillWaiting(task, room);

        UUID me = SecurityUtils.getCurrentUserId();
        LocalDateTime now = ShiftTimeUtils.nowAt(locationTimezoneOf(task));
        ClaimCheck check = checkClaim(me, task.getLocationId(), now, true);    // ② khóa ca, ③ đếm việc
        if (!check.allowed()) {
            throw new BusinessException(check.reason());
        }
        assertCanReceiveTask(me, task, check.workDay());                       // lưới chung với gán tay

        startTask(task, Set.of(me), check.workDay(), TaskAssignmentSource.SELF_CLAIM);
        roomStatusService.startCleaning(room, task.getId());                   // Chờ dọn → Đang dọn, Hệ thống
        log.info("Staff {} tự nhận task {} ngày {}", me, id, check.workDay());
        return toResponse(taskRepository.save(task));
    }

    /**
     * RM-22 — người đã tự nhận trả lại việc khi chưa bấm hoàn thành, bắt buộc lý do (BR-HK-17). Nhóm còn
     * người khác (Quản lý / Giám sát thêm vào — BR-HK-18) thì chỉ rút tên người trả, việc và phòng đứng yên;
     * hết người thì việc về hàng chờ và phòng về «Chờ dọn», nguồn Nhân viên dọn (BR-ROOM-02 bản sửa).
     * Lần trả nào cũng ghi một dòng {@link HousekeepingTaskReturn}.
     *
     * <p>Không cần đang trong ca: việc tồn đọng từ hôm qua vẫn trả được. Khóa dòng việc như mọi đường ghi —
     * đồng đội có thể đang bấm «Hoàn thành» cùng lúc.
     */
    @Transactional
    public HousekeepingTaskResponse returnTask(UUID id, ReturnTaskRequest request) {
        HousekeepingTask task = getOwnedTaskForUpdate(id);
        UUID me = SecurityUtils.getCurrentUserId();
        if (!task.isAssignedTo(me)) {
            throw new ResourceNotFoundException("HousekeepingTask", "id", id);   // như assertCanView
        }
        assertReturnable(task, me);
        String reason = normalize(request.getReason());
        if (reason == null) {
            throw new BusinessException("Nhập lý do trả lại việc.");
        }

        task.getAssigneeIds().remove(me);
        boolean released = !task.isAssigned();
        if (released) {
            release(task, UnassignedReason.SELF_RETURNED);
            // Việc vẫn MỞ (Chưa phân công) nên không đi qua hook sinh việc mới — như unassignTask.
            roomStatusService.returnToQueue(roomOf(task), task.getId(), reason);
        }
        returnRepository.save(taskReturn(task, me, reason, released));
        log.info("Staff {} trả lại task {} — {}", me, id, released ? "về hàng chờ" : "nhóm còn người");
        return toResponse(taskRepository.save(task));
    }

    /**
     * Lịch sử trả lại của một việc, mới nhất trước — liên kết "Lịch sử trả lại" trên thẻ việc (BR-HK-17).
     * Cùng phạm vi xem với {@link #getTask}.
     */
    @Transactional(readOnly = true)
    public List<TaskReturnResponse> getTaskReturns(UUID taskId) {
        HousekeepingTask task = getOwnedTask(taskId);
        assertCanView(task);

        List<HousekeepingTaskReturn> returns =
            returnRepository.findByTenantIdAndTaskIdOrderByReturnedAtDesc(task.getTenantId(), taskId);
        Map<UUID, String> names = staffNamesOf(
            returns.stream().map(HousekeepingTaskReturn::getStaffId).collect(Collectors.toSet()));
        return returns.stream()
            .map(taskReturn -> TaskReturnResponse.fromEntity(taskReturn, names.get(taskReturn.getStaffId())))
            .toList();
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

        String staffName = userRepository.findByIdAndTenantId(staffId, tasks.get(0).getTenantId())
            .map(User::getFullName)
            .orElse("Nhân viên này");
        throw new BusinessException(String.format(
            "%s đang giữ %d việc dọn ngày %s (phòng %s). Gỡ người khỏi các việc đó ở màn "
                + "«Công việc dọn phòng» trước khi gỡ, xóa hoặc dời ca này.",
            staffName, tasks.size(), date.format(DAY), roomNumbersOf(tasks)));
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
        HousekeepingTask task = getOwnedTaskForUpdate(id);
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
     * (BR-HK-12). Cùng phạm vi xem với {@link #getTask}, cộng thêm nhân viên Dọn dẹp cùng khách sạn —
     * xem {@link #assertCanViewInspection}.
     */
    @Transactional(readOnly = true)
    public InspectionRecordResponse getInspection(UUID taskId) {
        HousekeepingTask task = getOwnedTask(taskId);
        assertCanViewInspection(task);

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
     * Người này có tự nhận việc được lúc này không — BR-HK-15, BR-HK-16. Dùng chung cho màn danh sách
     * ({@link #getOpenTasks}) và lệnh nhận ({@link #claimTask}) để hai nơi không bao giờ nói khác nhau.
     * Kiểm theo thứ tự, dừng ở điều đầu tiên không đạt để câu báo luôn chỉ đúng việc cần làm tiếp:
     * <ol>
     *   <li>có ca thuộc ngày làm hiện tại tại khách sạn mình (kể cả ca đêm từ hôm qua — BR-SCH-03);</li>
     *   <li>một ca trong đó đã vào ca và chưa kết thúc ca;</li>
     *   <li>không giữ việc «Đang thực hiện» nào, ở MỌI ngày, kể cả việc được gán tay — «Chờ kiểm tra»
     *       không tính vì phần của người dọn đã xong.</li>
     * </ol>
     *
     * @param lock lệnh nhận khóa ca của mình TRƯỚC khi đếm việc đang làm — xem {@link #claimTask}
     */
    private ClaimCheck checkClaim(UUID staffId, UUID locationId, LocalDateTime now, boolean lock) {
        List<Shift> shifts = cleaningWorkDayRule.currentWorkShifts(staffId, locationId, now, lock);
        if (shifts.isEmpty()) {
            return ClaimCheck.blocked("Hôm nay bạn không có ca nên chưa nhận việc được.", null);
        }
        Optional<Shift> working = shifts.stream()
            .filter(shift -> shift.isCheckedIn() && shift.getCheckOutAt() == null)
            .findFirst();
        if (working.isEmpty()) {
            boolean notCheckedInYet = shifts.stream().anyMatch(shift -> !shift.isCheckedIn());
            return ClaimCheck.blocked(notCheckedInYet
                ? "Bạn cần bấm «Vào ca» trước khi nhận việc."
                : "Bạn đã kết thúc ca nên không nhận thêm việc được.", null);
        }

        LocalDate workDay = working.get().getShiftDate();
        List<HousekeepingTask> holding = taskRepository.search(SecurityUtils.getCurrentTenantId(), null, staffId,
            HousekeepingTaskStatus.IN_PROGRESS, null, null, null, null, Pageable.unpaged()).getContent();
        if (!holding.isEmpty()) {
            return ClaimCheck.blocked(String.format(
                "Bạn đang dọn phòng %s — bấm «Hoàn thành» xong mới nhận việc mới.", roomNumbersOf(holding)), workDay);
        }
        return new ClaimCheck(true, null, workDay);
    }

    /**
     * Kết quả của {@link #checkClaim}.
     *
     * @param reason  câu cho người dùng khi chưa nhận được; {@code null} khi nhận được
     * @param workDay ngày làm của ca đang làm (đã vào, chưa kết thúc); {@code null} khi không có ca như vậy
     */
    private record ClaimCheck(boolean allowed, String reason, LocalDate workDay) {

        static ClaimCheck blocked(String reason, LocalDate workDay) {
            return new ClaimCheck(false, reason, workDay);
        }
    }

    /**
     * BR-HK-18: chỉ việc còn «Chưa phân công» mới tự nhận được — người tới sau không tự vào nhóm của việc
     * đã có người; muốn thêm người thì Quản lý / Giám sát làm.
     */
    private static void assertStillWaiting(HousekeepingTask task, Room room) {
        if (task.getStatus() == HousekeepingTaskStatus.CANCELLED) {
            throw new BusinessException(String.format(
                "Việc dọn phòng %s đã bị hủy vì phòng chuyển sang Không khả dụng.", room.getRoomNumber()));
        }
        if (task.getStatus() != HousekeepingTaskStatus.UNASSIGNED) {
            throw new BusinessException(String.format(
                "Phòng %s vừa có người nhận. Danh sách đã được cập nhật — chọn phòng khác.", room.getRoomNumber()));
        }
    }

    /**
     * BR-HK-17: chỉ trả lại được việc CHƯA bấm hoàn thành, và chỉ người đã tự nhận nó — cách giao là tự nhận
     * và người giao là chính mình. Người được Quản lý / Giám sát giao, hay được thêm vào nhóm của việc người
     * khác tự nhận, thì nhờ Quản lý gỡ tên.
     */
    private static void assertReturnable(HousekeepingTask task, UUID staffId) {
        if (task.getStatus() == HousekeepingTaskStatus.CANCELLED) {
            throw new BusinessException("Việc dọn này đã bị hủy vì phòng chuyển sang Không khả dụng.");
        }
        if (task.getStatus() != HousekeepingTaskStatus.IN_PROGRESS) {
            throw new BusinessException("Việc này đã báo hoàn thành nên không trả lại được.");
        }
        if (task.getAssignmentSource() != TaskAssignmentSource.SELF_CLAIM || !staffId.equals(task.getAssignedBy())) {
            throw new BusinessException("Chỉ trả lại được việc bạn tự nhận. "
                + "Việc do quản lý hoặc giám sát giao thì nhờ quản lý gỡ tên.");
        }
    }

    /**
     * Việc chưa phân công BẮT ĐẦU: có nhóm, có ngày làm, ghi ai giao, lúc nào và giao bằng cách nào
     * (BR-HK-12 sửa M2). Dùng chung cho gán tay và tự nhận. BR-HK-06: không có trạng thái "đã gán" riêng —
     * gán là bắt đầu thực hiện. Phòng do nơi gọi chuyển trạng thái.
     */
    private static void startTask(HousekeepingTask task, Set<UUID> staffIds, LocalDate workDay,
                                  TaskAssignmentSource source) {
        task.getAssigneeIds().clear();
        task.getAssigneeIds().addAll(staffIds);
        task.setAssignedDate(workDay);
        task.setAssignedBy(SecurityUtils.getCurrentUserId());
        task.setAssignedAt(LocalDateTime.now());
        task.setAssignmentSource(source);
        task.setUnassignedReason(null);
        task.setStatus(HousekeepingTaskStatus.IN_PROGRESS);
    }

    /** Cách giao của người đang bấm "Giao việc": Giám sát vận hành hay Quản lý (Admin tính là Quản lý). */
    private static TaskAssignmentSource coordinatorSource() {
        return SecurityUtils.isSupervisor() ? TaskAssignmentSource.SUPERVISION : TaskAssignmentSource.MANAGER;
    }

    /** "201, 305" — số phòng của các việc, xếp theo thứ tự tự nhiên; dùng trong câu báo lỗi. */
    private String roomNumbersOf(Collection<HousekeepingTask> tasks) {
        Map<UUID, Room> rooms = loadRooms(tasks);
        return tasks.stream()
            .map(task -> rooms.get(task.getRoomId()))
            .filter(Objects::nonNull)
            .map(Room::getRoomNumber)
            .sorted(ROOM_NUMBER_ORDER)
            .collect(Collectors.joining(", "));
    }

    /**
     * Ai được XEM một task — BR-PERM-05. Nhân viên chỉ thấy việc có mình trong nhóm dọn, Manager và
     * Giám sát vận hành (BR-PERM-07) thấy task trong Location của mình, Giám đốc thấy cả Tenant. Ngoài
     * phạm vi trả 404 để không lộ task có tồn tại. Dùng chung cho {@link #getTask},
     * {@link #getInspection} và {@link #getTaskReturns}.
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
     * Ai được xem BIÊN BẢN kiểm tra: như {@link #assertCanView}, cộng thêm nhân viên Dọn dẹp cùng khách
     * sạn. Việc dọn lại nằm trong danh sách chờ nhận của mọi người (BR-HK-14), nên người định nhận cần
     * đọc được lý do lần trước không đạt dù không ở trong nhóm dọn lần đó (BR-HK-12). Biên bản chỉ nói về
     * độ sạch của phòng, không có gì riêng tư của người dọn trước.
     */
    private void assertCanViewInspection(HousekeepingTask task) {
        boolean cleanerOfSameHotel = SecurityUtils.hasRole(Role.STAFF)
            && SecurityUtils.hasPermission(StaffPermission.HOUSEKEEPING)
            && task.getLocationId().equals(SecurityUtils.getCurrentLocationId());
        if (!cleanerOfSameHotel) {
            assertCanView(task);
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

    /** Một dòng lịch sử trả lại — người trả luôn là người đang đăng nhập (BR-HK-17). */
    private static HousekeepingTaskReturn taskReturn(HousekeepingTask task, UUID staffId, String reason,
                                                     boolean released) {
        return HousekeepingTaskReturn.builder()
            .tenantId(task.getTenantId())
            .taskId(task.getId())
            .staffId(staffId)
            .reason(reason)
            .returnedAt(LocalDateTime.now())
            .taskReleased(released)
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
            // Đường trả lại ghi lý do người dùng nhập (returnTask), không dùng câu này — chỉ để switch đủ nhánh.
            case SELF_RETURNED  -> "Người dọn phòng trả lại việc";
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

    /** Task về Chưa phân công — không còn ai trong nhóm, không còn ngày làm, không còn cách giao. */
    private static void release(HousekeepingTask task, UnassignedReason reason) {
        task.setStatus(HousekeepingTaskStatus.UNASSIGNED);
        task.getAssigneeIds().clear();
        task.setAssignedDate(null);
        task.setAssignedBy(null);
        task.setAssignedAt(null);
        task.setAssignmentSource(null);
        task.setUnassignedReason(reason);
    }

    /** Chốt chặn cách ly Tenant: không bao giờ tải task bằng findById trần. */
    private HousekeepingTask getOwnedTask(UUID id) {
        return taskRepository.findByIdAndTenantId(id, SecurityUtils.getCurrentTenantId())
            .orElseThrow(() -> new ResourceNotFoundException("HousekeepingTask", "id", id));
    }

    /**
     * Như {@link #getOwnedTask} nhưng khóa dòng việc tới hết transaction — mọi đường GHI việc dọn dùng
     * bản này để hai người không cùng giao / nhận / gỡ một việc (BR-HK-16, BR-HK-18). Gọi làm lần tải việc
     * ĐẦU TIÊN của transaction (xem javadoc repository).
     */
    private HousekeepingTask getOwnedTaskForUpdate(UUID id) {
        return taskRepository.findByIdAndTenantIdForUpdate(id, SecurityUtils.getCurrentTenantId())
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
     * Phòng, loại phòng, tên người dọn, cờ «Tồn đọng» và số lần bị trả lại cho CẢ một trang task, mỗi loại
     * một câu truy vấn. Mọi id lấy từ chính các task đã lọc theo Tenant nên không cần lọc lại.
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

        Map<UUID, String> staffNames = staffNamesOf(tasks.stream()
            .flatMap(task -> task.getAssigneeIds().stream())
            .collect(Collectors.toSet()));

        return new ResponseLookups(rooms, typeNames, staffNames, cleaningWorkDayRule.overdueTaskIds(tasks),
            countReturns(tasks));
    }

    /** Họ tên theo id, một câu truy vấn; người không tra được không có mặt. */
    private Map<UUID, String> staffNamesOf(Set<UUID> staffIds) {
        return staffIds.isEmpty() ? Map.of()
            : userRepository.findAllById(staffIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
    }

    /** Số lần bị trả lại của từng việc (BR-HK-17), một câu đếm; việc chưa bị trả lần nào không có mặt. */
    private Map<UUID, Long> countReturns(Collection<HousekeepingTask> tasks) {
        Set<UUID> taskIds = tasks.stream().map(HousekeepingTask::getId).filter(Objects::nonNull)
            .collect(Collectors.toSet());
        return taskIds.isEmpty() ? Map.of()
            : returnRepository.countGroupedByTask(taskIds).stream()
                .collect(Collectors.toMap(TaskReturnCount::getTaskId, TaskReturnCount::getTotal));
    }

    private record ResponseLookups(Map<UUID, Room> rooms, Map<UUID, String> typeNames,
                                   Map<UUID, String> staffNames, Set<UUID> overdueTaskIds,
                                   Map<UUID, Long> returnCounts) {

        HousekeepingTaskResponse toResponse(HousekeepingTask task) {
            Room room = rooms.get(task.getRoomId());
            return HousekeepingTaskResponse.fromEntity(
                task, room, room == null ? null : typeNames.get(room.getRoomTypeId()), staffNames,
                overdueTaskIds.contains(task.getId()), returnCounts.getOrDefault(task.getId(), 0L).intValue());
        }
    }
}
