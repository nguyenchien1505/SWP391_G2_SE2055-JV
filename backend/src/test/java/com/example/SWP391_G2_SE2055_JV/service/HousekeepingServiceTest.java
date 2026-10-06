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
import com.example.SWP391_G2_SE2055_JV.enums.PositionType;
import com.example.SWP391_G2_SE2055_JV.enums.StaffPermission;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
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
import com.example.SWP391_G2_SE2055_JV.config.CustomUserDetails;
import com.example.SWP391_G2_SE2055_JV.support.TestAuth;
import com.example.SWP391_G2_SE2055_JV.utils.ShiftTimeUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Lịch dọn phòng: BR-HK-02, BR-HK-03, BR-HK-06, BR-HK-07, BR-PERM-05, hệ quả lên trạng thái phòng
 * (BR-ROOM-02) — và mô hình MỘT PHÒNG NHIỀU NGƯỜI DỌN (V4, chốt 05/10/2026, thay DM-04).
 *
 * <p>Chỉ còn việc dọn sau khi khách trả phòng, nên mọi bước của việc dọn đều kéo theo trạng thái
 * phòng. {@link RoomStatusService} được mock để kiểm đúng MỐC kéo theo đó: giao lần đầu, gỡ người
 * cuối cùng, báo xong, nghiệm thu — còn thêm/bớt người giữa chừng thì phòng đứng yên. Luật đổi trạng
 * thái phòng đã test riêng ở {@code RoomStatusServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class HousekeepingServiceTest {

    private static final UUID TENANT_ID         = UUID.randomUUID();
    private static final UUID LOCATION_ID       = UUID.randomUUID();
    private static final UUID OTHER_LOCATION_ID = UUID.randomUUID();
    private static final UUID ROOM_ID           = UUID.randomUUID();
    private static final UUID ROOM_TYPE_ID      = UUID.randomUUID();
    private static final UUID STAFF_ID          = UUID.randomUUID();
    private static final UUID STAFF_2_ID        = UUID.randomUUID();
    private static final UUID POSITION_ID       = UUID.randomUUID();

    /** Múi giờ của khách sạn mẫu; "hôm nay" trong test luôn tính theo đúng mốc này (BR-SCH-17). */
    private static final String TIMEZONE = "Asia/Ho_Chi_Minh";

    @Mock HousekeepingTaskRepository taskRepository;
    @Mock UserRepository             userRepository;
    @Mock ShiftRepository            shiftRepository;
    @Mock RoomRepository             roomRepository;
    @Mock RoomTypeRepository         roomTypeRepository;
    @Mock LocationRepository         locationRepository;
    @Mock AssignableStaffRepository  assignableStaffRepository;
    @Mock InspectionRecordRepository inspectionRepository;
    @Mock RoomStatusService          roomStatusService;

    @InjectMocks HousekeepingService service;

    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = ShiftTimeUtils.todayAt(TIMEZONE);
    }

    @AfterEach
    void logout() {
        TestAuth.logout();
    }

    // ── Giao việc — BR-HK-02, BR-HK-03 ──────────────────────────────────────

    @Nested
    class AssignTask {

        @BeforeEach
        void loginAsManager() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
        }

        /** BR-ROOM-02: giao người cho việc đang chờ là bắt đầu dọn — phòng sang «Đang dọn» ngay. */
        @Test
        void shouldStartCleaningWhenAssigningUnassignedTask() {
            HousekeepingTask task = unassignedTask();
            Room room = stubAssignReady(task, housekeepingStaff());

            HousekeepingTaskResponse response = service.assignTask(task.getId(), request(today, STAFF_ID));

            assertThat(response.getStatus()).isEqualTo(HousekeepingTaskStatus.IN_PROGRESS);
            assertThat(task.getAssigneeIds()).containsExactly(STAFF_ID);
            assertThat(task.getAssignedDate()).isEqualTo(today);
            verify(roomStatusService).startCleaning(room, task.getId());
        }

        /** Một phòng nhiều người dọn: một lần giao được cả nhóm, phòng chỉ chuyển trạng thái MỘT lần. */
        @Test
        void shouldAssignSeveralCleanersAtOnce() {
            HousekeepingTask task = unassignedTask();
            Room room = stubAssignReady(task, housekeepingStaff(), secondStaff());

            service.assignTask(task.getId(), request(today, STAFF_ID, STAFF_2_ID));

            assertThat(task.getAssigneeIds()).containsExactlyInAnyOrder(STAFF_ID, STAFF_2_ID);
            assertThat(task.getStatus()).isEqualTo(HousekeepingTaskStatus.IN_PROGRESS);
            verify(roomStatusService).startCleaning(room, task.getId());
        }

        /** Tất cả hoặc không: một người không đủ điều kiện thì không giao cho ai, phòng đứng yên. */
        @Test
        void shouldRejectWholeTeamWhenOnePersonHasNoShift() {
            HousekeepingTask task = unassignedTask();
            stubTask(task);
            stubLocation();
            stubStaff(housekeepingStaff());
            stubStaff(secondStaff());
            when(shiftRepository.existsByStaffIdAndShiftDate(STAFF_ID, today)).thenReturn(true);
            when(shiftRepository.existsByStaffIdAndShiftDate(STAFF_2_ID, today)).thenReturn(false);

            assertThatThrownBy(() -> service.assignTask(task.getId(), request(today, STAFF_ID, STAFF_2_ID)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Nguyễn Thị Hoa không có ca làm việc");

            assertThat(task.getStatus()).isEqualTo(HousekeepingTaskStatus.UNASSIGNED);
            assertThat(task.getAssigneeIds()).isEmpty();
            verifyNoInteractions(roomStatusService);
        }

        /** BR-HK-03: có ca trong ngày là điều kiện bắt buộc, không cần khớp khung giờ. */
        @Test
        void shouldRejectAssignWhenStaffHasNoShiftThatDay() {
            HousekeepingTask task = unassignedTask();
            stubTask(task);
            stubLocation();
            stubStaff(housekeepingStaff());
            when(shiftRepository.existsByStaffIdAndShiftDate(STAFF_ID, today)).thenReturn(false);

            assertThatThrownBy(() -> service.assignTask(task.getId(), request(today, STAFF_ID)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ca làm việc")
                .hasMessageNotContaining("BR-");
            verifyNoInteractions(roomStatusService);
        }

        /** BR-HK-02: KHÔNG giới hạn số việc mỗi người — không được có truy vấn đếm nào. */
        @Test
        void shouldNotLimitNumberOfTasksPerStaff() {
            HousekeepingTask task = unassignedTask();
            stubAssignReady(task, housekeepingStaff());

            service.assignTask(task.getId(), request(today, STAFF_ID));

            verify(taskRepository, never()).countOfStaff(any(), any());
            verify(taskRepository, never()).search(any(), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void shouldRejectAssignWhenStaffNotHousekeeping() {
            HousekeepingTask task = unassignedTask();
            stubTask(task);
            stubLocation();
            User receptionist = housekeepingStaff();
            receptionist.setPermissions(EnumSet.of(StaffPermission.RECEPTION));
            stubStaff(receptionist);

            assertThatThrownBy(() -> service.assignTask(task.getId(), request(today, STAFF_ID)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Dọn dẹp");
        }

        /** Nhân viên đa nhiệm: được tick cả Lễ tân và Dọn dẹp thì vẫn nhận việc dọn. */
        @Test
        void shouldAssignStaffHoldingHousekeepingAmongSeveralPermissions() {
            HousekeepingTask task = unassignedTask();
            User multiRole = housekeepingStaff();
            multiRole.setPermissions(EnumSet.of(StaffPermission.RECEPTION, StaffPermission.HOUSEKEEPING));
            stubAssignReady(task, multiRole);

            service.assignTask(task.getId(), request(today, STAFF_ID));

            assertThat(task.getAssigneeIds()).containsExactly(STAFF_ID);
        }

        @Test
        void shouldRejectAssignWhenStaffInAnotherLocation() {
            HousekeepingTask task = unassignedTask();
            stubTask(task);
            stubLocation();
            User elsewhere = housekeepingStaff();
            elsewhere.setLocationId(OTHER_LOCATION_ID);
            stubStaff(elsewhere);

            assertThatThrownBy(() -> service.assignTask(task.getId(), request(today, STAFF_ID)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không thuộc Location");
        }

        @Test
        void shouldRejectAssignWhenStaffTerminated() {
            HousekeepingTask task = unassignedTask();
            stubTask(task);
            stubLocation();
            User terminated = housekeepingStaff();
            terminated.setStatus(UserStatus.TERMINATED);
            stubStaff(terminated);

            assertThatThrownBy(() -> service.assignTask(task.getId(), request(today, STAFF_ID)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đang làm việc");
        }

        /** BR-SCH-17: "hôm nay" theo múi giờ KHÁCH SẠN, nên phải tra Location chứ không dùng giờ máy chủ. */
        @Test
        void shouldRejectAssignForPastDateInLocationTimezone() {
            HousekeepingTask task = unassignedTask();
            stubTask(task);
            stubLocation();

            assertThatThrownBy(() -> service.assignTask(task.getId(), request(today.minusDays(1), STAFF_ID)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ngày đã qua");

            verify(locationRepository).findByIdAndTenantId(LOCATION_ID, TENANT_ID);
            verifyNoInteractions(userRepository, roomStatusService);
        }

        /**
         * Q5: giao việc dọn là phòng sang «Đang dọn» NGAY, nên giao trước cho ngày mai sẽ khiến phòng
         * hiện sai trạng thái suốt hôm nay.
         */
        @Test
        void shouldRejectFutureDate() {
            HousekeepingTask task = unassignedTask();
            stubTask(task);
            stubLocation();

            assertThatThrownBy(() -> service.assignTask(task.getId(), request(today.plusDays(1), STAFF_ID)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chỉ gán được cho hôm nay")
                .hasMessageNotContaining("BR-");
            verifyNoInteractions(roomStatusService);
        }

        /**
         * Giao thêm vào việc đang làm: chỉ người MỚI phải qua kiểm tra (strict stub — tra người cũ sẽ
         * làm test vỡ), phòng đã «Đang dọn» nên đứng yên.
         */
        @Test
        void shouldAddNewcomersToTaskInProgressWithoutTouchingRoom() {
            HousekeepingTask task = inProgressTask(STAFF_ID);
            stubTask(task);
            stubLocation();
            stubStaff(secondStaff());
            when(shiftRepository.existsByStaffIdAndShiftDate(STAFF_2_ID, today)).thenReturn(true);
            stubSave();

            service.assignTask(task.getId(), request(today, STAFF_ID, STAFF_2_ID));

            assertThat(task.getAssigneeIds()).containsExactlyInAnyOrder(STAFF_ID, STAFF_2_ID);
            assertThat(task.getStatus()).isEqualTo(HousekeepingTaskStatus.IN_PROGRESS);
            verifyNoInteractions(roomStatusService);
        }

        @Test
        void shouldRejectAddingWhenEveryoneIsAlreadyInTeam() {
            HousekeepingTask task = inProgressTask(STAFF_ID);
            stubTask(task);

            assertThatThrownBy(() -> service.assignTask(task.getId(), request(today, STAFF_ID)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã có trong nhóm");
        }

        /** Cả nhóm dọn cùng một ngày — người thêm vào phải đúng ngày của việc đó. */
        @Test
        void shouldRejectAddingForAnotherDay() {
            HousekeepingTask task = inProgressTask(STAFF_ID);
            task.setAssignedDate(today.minusDays(1));   // việc tồn từ hôm qua (BR-HK-04)
            stubTask(task);

            assertThatThrownBy(() -> service.assignTask(task.getId(), request(today, STAFF_2_ID)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cùng ngày");
            assertThat(task.getAssigneeIds()).containsExactly(STAFF_ID);
        }

        @Test
        void shouldRejectAssignWhenTaskPendingInspection() {
            HousekeepingTask task = task(HousekeepingTaskStatus.PENDING_INSPECTION, STAFF_ID);
            stubTask(task);

            assertThatThrownBy(() -> service.assignTask(task.getId(), request(today, STAFF_2_ID)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chờ kiểm tra");
            verifyNoInteractions(roomStatusService);
        }

        @Test
        void shouldRejectAssignWhenTaskClosed() {
            HousekeepingTask task = task(HousekeepingTaskStatus.COMPLETED, STAFF_ID);
            stubTask(task);

            assertThatThrownBy(() -> service.assignTask(task.getId(), request(today, STAFF_2_ID)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã đóng");
            verifyNoInteractions(roomStatusService);
        }
    }

    // ── Gỡ người — BR-HK-07 ─────────────────────────────────────────────────

    @Nested
    class UnassignTask {

        @BeforeEach
        void loginAsManager() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
        }

        /** Còn người khác trong nhóm thì việc vẫn tiếp tục, phòng vẫn «Đang dọn». */
        @Test
        void shouldRemoveOnePersonAndKeepTaskGoing() {
            HousekeepingTask task = inProgressTask(STAFF_ID, STAFF_2_ID);
            stubTask(task);
            stubSave();

            service.unassignTask(task.getId(), STAFF_2_ID);

            assertThat(task.getStatus()).isEqualTo(HousekeepingTaskStatus.IN_PROGRESS);
            assertThat(task.getAssigneeIds()).containsExactly(STAFF_ID);
            assertThat(task.getAssignedDate()).isEqualTo(today);
            verifyNoInteractions(roomStatusService);
        }

        /** Gỡ người cuối cùng: việc về hàng chờ, phòng về «Chờ dọn»; việc VẪN MỞ nên không sinh việc mới. */
        @Test
        void shouldRevertRoomToDirtyWhenLastPersonIsRemoved() {
            HousekeepingTask task = inProgressTask(STAFF_ID);
            Room room = stubTaskAndRoom(task);

            service.unassignTask(task.getId(), STAFF_ID);

            assertThat(task.getStatus()).isEqualTo(HousekeepingTaskStatus.UNASSIGNED);
            assertThat(task.getAssigneeIds()).isEmpty();
            assertThat(task.getAssignedDate()).isNull();
            assertThat(task.getUnassignedReason()).isEqualTo(UnassignedReason.MANAGER_MANUAL);
            verify(roomStatusService).revertToDirty(room, task.getId(), "Quản lý gỡ người khỏi việc dọn");
        }

        /** Không chỉ định người = gỡ cả nhóm. */
        @Test
        void shouldRemoveWholeTeamWhenNoStaffGiven() {
            HousekeepingTask task = inProgressTask(STAFF_ID, STAFF_2_ID);
            Room room = stubTaskAndRoom(task);

            service.unassignTask(task.getId(), null);

            assertThat(task.getStatus()).isEqualTo(HousekeepingTaskStatus.UNASSIGNED);
            assertThat(task.getAssigneeIds()).isEmpty();
            verify(roomStatusService).revertToDirty(room, task.getId(), "Quản lý gỡ người khỏi việc dọn");
        }

        @Test
        void shouldRejectRemovingSomeoneOutsideTeam() {
            HousekeepingTask task = inProgressTask(STAFF_ID);
            stubTask(task);

            assertThatThrownBy(() -> service.unassignTask(task.getId(), STAFF_2_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không có trong nhóm");
            assertThat(task.getAssigneeIds()).containsExactly(STAFF_ID);
            verifyNoInteractions(roomStatusService);
        }

        @Test
        void shouldRejectUnassignWhenTaskNotInProgress() {
            HousekeepingTask task = task(HousekeepingTaskStatus.PENDING_INSPECTION, STAFF_ID);
            stubTask(task);

            assertThatThrownBy(() -> service.unassignTask(task.getId(), STAFF_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đang thực hiện");
            verifyNoInteractions(roomStatusService);
        }
    }

    // ── Hoàn thành — BR-HK-06, BR-PERM-05 ───────────────────────────────────

    @Nested
    class CompleteTask {

        /** BR-HK-06: việc dọn CHƯA xong — còn chờ Manager nghiệm thu. */
        @Test
        void shouldMoveTaskToPendingInspectionOnComplete() {
            HousekeepingTask task = inProgressTask(STAFF_ID);
            TestAuth.loginAs(STAFF_ID, Role.STAFF, TENANT_ID, LOCATION_ID, PositionType.HOUSEKEEPING);
            Room room = stubTaskAndRoom(task);

            service.completeTask(task.getId());

            assertThat(task.getStatus()).isEqualTo(HousekeepingTaskStatus.PENDING_INSPECTION);
            assertThat(task.getCompletedAt()).isNull();   // chưa nghiệm thu thì chưa có mốc hoàn thành
            verify(roomStatusService).markPendingInspection(room, task.getId());
        }

        /** Chốt 05/10/2026: một người trong nhóm bấm là xong cho cả nhóm, nhóm giữ nguyên. */
        @Test
        void shouldLetAnyTeamMemberCompleteForWholeTeam() {
            HousekeepingTask task = inProgressTask(STAFF_ID, STAFF_2_ID);
            TestAuth.loginAs(STAFF_2_ID, Role.STAFF, TENANT_ID, LOCATION_ID, PositionType.HOUSEKEEPING);
            Room room = stubTaskAndRoom(task);

            service.completeTask(task.getId());

            assertThat(task.getStatus()).isEqualTo(HousekeepingTaskStatus.PENDING_INSPECTION);
            assertThat(task.getAssigneeIds()).containsExactlyInAnyOrder(STAFF_ID, STAFF_2_ID);
            verify(roomStatusService).markPendingInspection(room, task.getId());
        }

        /** BR-PERM-05: người ngoài nhóm dọn không bấm hoàn thành được. */
        @Test
        void shouldRejectCompleteByStaffOutsideTeam() {
            HousekeepingTask task = inProgressTask(STAFF_ID);
            TestAuth.loginAsStaff(TENANT_ID, LOCATION_ID, PositionType.HOUSEKEEPING);   // id khác
            stubTask(task);

            assertThatThrownBy(() -> service.completeTask(task.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("trong nhóm dọn");
            verifyNoInteractions(roomStatusService);
        }
    }

    // ── Kiểm tra phòng sau dọn — BR-HK-06, BR-HK-08, BR-HK-12 ──────────

    @Nested
    class InspectTask {

        private CustomUserDetails manager;

        @BeforeEach
        void loginAsManager() {
            manager = TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
        }

        /** BR-HK-06: ĐẠT → task xong hẳn, phòng sang «Trống / Sẵn sàng», KHÔNG sinh việc mới. */
        @Test
        void shouldCompleteTaskAndMakeRoomAvailableWhenInspectionPasses() {
            HousekeepingTask task = pendingInspectionTask();
            Room room = stubInspectReady(task);

            InspectionRecordResponse record =
                service.inspectTask(task.getId(), inspectRequest(InspectionResult.PASS, null));

            assertThat(task.getStatus()).isEqualTo(HousekeepingTaskStatus.COMPLETED);
            assertThat(task.getCompletedAt()).isNotNull();
            assertThat(record.getNextTaskId()).isNull();
            verify(roomStatusService).applyInspection(room, InspectionResult.PASS, null, task.getId());
            verify(taskRepository, never()).save(any(HousekeepingTask.class));
        }

        /** BR-HK-08: không đạt mà bỏ trống lý do thì chặn trước khi đụng vào phòng. */
        @Test
        void shouldRequireReasonWhenInspectionFails() {
            HousekeepingTask task = pendingInspectionTask();
            stubTask(task);

            assertThatThrownBy(() ->
                service.inspectTask(task.getId(), inspectRequest(InspectionResult.FAIL, "   ")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("bắt buộc nhập lý do")
                .hasMessageNotContaining("BR-");

            assertThat(task.getStatus()).isEqualTo(HousekeepingTaskStatus.PENDING_INSPECTION);
            verifyNoInteractions(roomStatusService, inspectionRepository);
        }

        /** BR-HK-12: việc dọn lại là task MỚI, nguồn INSPECTION_FAILED, trỏ ngược về task gốc. */
        @Test
        void shouldCreateFollowUpTaskLinkedToParentWhenInspectionFails() {
            HousekeepingTask task = pendingInspectionTask();
            Room room = stubInspectReady(task);

            InspectionRecordResponse record =
                service.inspectTask(task.getId(), inspectRequest(InspectionResult.FAIL, "Nhà tắm còn bẩn"));

            ArgumentCaptor<HousekeepingTask> created = ArgumentCaptor.forClass(HousekeepingTask.class);
            verify(taskRepository).save(created.capture());
            HousekeepingTask next = created.getValue();
            assertThat(next.getCreatedSource()).isEqualTo(TaskCreatedSource.INSPECTION_FAILED);
            assertThat(next.getTaskType()).isEqualTo(HousekeepingTaskType.CHECKOUT);
            assertThat(next.getStatus()).isEqualTo(HousekeepingTaskStatus.UNASSIGNED);
            assertThat(next.getAssigneeIds()).isEmpty();
            assertThat(next.getParentTaskId()).isEqualTo(task.getId());
            assertThat(next.getRoomId()).isEqualTo(ROOM_ID);

            // BR-HK-06: task gốc vẫn COMPLETED — không có trạng thái FAILED.
            assertThat(task.getStatus()).isEqualTo(HousekeepingTaskStatus.COMPLETED);
            assertThat(record.getNextTaskId()).isEqualTo(next.getId());
            assertThat(record.getReason()).isEqualTo("Nhà tắm còn bẩn");
            verify(roomStatusService)
                .applyInspection(room, InspectionResult.FAIL, "Nhà tắm còn bẩn", task.getId());
        }

        /**
         * BR-HK-11 + {@code uk_hk_open_task_per_room_type}: task gốc và việc dọn lại dùng chung
         * khóa {@code phòng:CHECKOUT}. Phải đẩy UPDATE task gốc xuống DB TRƯỚC khi INSERT task
         * mới, nếu không Hibernate gộp flush và INSERT chạy trước UPDATE → 409.
         */
        @Test
        void shouldFlushOriginalTaskBeforeCreatingFollowUp() {
            HousekeepingTask task = pendingInspectionTask();
            stubInspectReady(task);

            service.inspectTask(task.getId(), inspectRequest(InspectionResult.FAIL, "Còn tóc trên sàn"));

            InOrder order = inOrder(taskRepository);
            order.verify(taskRepository).saveAndFlush(task);
            order.verify(taskRepository).save(any(HousekeepingTask.class));
        }

        @Test
        void shouldRecordCurrentManagerAsInspector() {
            HousekeepingTask task = pendingInspectionTask();
            stubInspectReady(task);

            service.inspectTask(task.getId(), inspectRequest(InspectionResult.PASS, null));

            ArgumentCaptor<InspectionRecord> saved = ArgumentCaptor.forClass(InspectionRecord.class);
            verify(inspectionRepository).save(saved.capture());
            assertThat(saved.getValue().getInspectorId()).isEqualTo(manager.getId());
            assertThat(saved.getValue().getTenantId()).isEqualTo(TENANT_ID);
            assertThat(saved.getValue().getInspectedAt()).isNotNull();
        }

        /** Chưa báo xong thì chưa có gì để nghiệm thu. */
        @Test
        void shouldRejectInspectingTaskStillInProgress() {
            HousekeepingTask task = inProgressTask(STAFF_ID);
            stubTask(task);

            assertThatThrownBy(() ->
                service.inspectTask(task.getId(), inspectRequest(InspectionResult.PASS, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chờ kiểm tra")
                .hasMessageNotContaining("BR-");
            verifyNoInteractions(roomStatusService);
        }

        /** Nghiệm thu đúng MỘT lần: task đã COMPLETED thì không kiểm lại được. */
        @Test
        void shouldRejectInspectingTaskNotPendingInspection() {
            HousekeepingTask task = pendingInspectionTask();
            task.setStatus(HousekeepingTaskStatus.COMPLETED);
            stubTask(task);

            assertThatThrownBy(() ->
                service.inspectTask(task.getId(), inspectRequest(InspectionResult.PASS, null)))
                .isInstanceOf(BusinessException.class);
            verifyNoInteractions(roomStatusService, inspectionRepository);
        }

        /** Q8: task ở Location khác trả 404 — không lộ task có tồn tại. */
        @Test
        void shouldThrowNotFoundWhenManagerInspectsTaskOfAnotherLocation() {
            HousekeepingTask task = pendingInspectionTask();
            task.setLocationId(OTHER_LOCATION_ID);
            stubTask(task);

            assertThatThrownBy(() ->
                service.inspectTask(task.getId(), inspectRequest(InspectionResult.PASS, null)))
                .isInstanceOf(ResourceNotFoundException.class);
            verifyNoInteractions(roomStatusService, inspectionRepository);
        }

        /** BR-HK-12: thẻ việc dọn lại tra ngược biên bản của task cha để hiện lý do. */
        @Test
        void shouldReturnInspectionOfTask() {
            HousekeepingTask task = pendingInspectionTask();
            stubTask(task);
            when(inspectionRepository.findByTenantIdAndTaskId(TENANT_ID, task.getId()))
                .thenReturn(Optional.of(InspectionRecord.builder()
                    .tenantId(TENANT_ID).taskId(task.getId()).roomId(ROOM_ID)
                    .inspectorId(manager.getId()).result(InspectionResult.FAIL)
                    .reason("Nhà tắm còn bẩn").inspectedAt(LocalDateTime.now()).build()));

            assertThat(service.getInspection(task.getId()).getReason()).isEqualTo("Nhà tắm còn bẩn");
        }

        @Test
        void shouldThrowNotFoundWhenTaskHasNoInspection() {
            HousekeepingTask task = pendingInspectionTask();
            stubTask(task);
            when(inspectionRepository.findByTenantIdAndTaskId(TENANT_ID, task.getId()))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getInspection(task.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── Gỡ ca tương lai — BR-HK-07, BR-SCH-17 ───────────────────────────────

    @Nested
    class ReleaseFutureTasks {

        /** Người bị gỡ là người dọn duy nhất: việc về hàng chờ, phòng về «Chờ dọn» với lý do tự giải thích. */
        @Test
        void shouldReleaseTaskAndRevertRoomWhenStaffWasOnlyCleaner() {
            HousekeepingTask alone = inProgressTask(STAFF_ID);
            when(taskRepository.findOfStaffAfter(STAFF_ID, HousekeepingTaskStatus.IN_PROGRESS, today))
                .thenReturn(List.of(alone));
            Room room = room();
            when(roomRepository.findByIdAndTenantIdAndActiveTrue(ROOM_ID, TENANT_ID)).thenReturn(Optional.of(room));

            int released = service.releaseFutureTasks(STAFF_ID, UnassignedReason.TERMINATION, today);

            assertThat(released).isEqualTo(1);
            assertThat(alone.getStatus()).isEqualTo(HousekeepingTaskStatus.UNASSIGNED);
            assertThat(alone.getUnassignedReason()).isEqualTo(UnassignedReason.TERMINATION);
            verify(roomStatusService).revertToDirty(room, alone.getId(), "Người dọn phòng đã nghỉ việc");
        }

        /** Nhóm còn người khác thì chỉ bớt người đó ra — việc tiếp tục, phòng đứng yên. */
        @Test
        void shouldOnlyLeaveTeamWhenOthersRemain() {
            HousekeepingTask shared = inProgressTask(STAFF_ID, STAFF_2_ID);
            when(taskRepository.findOfStaffAfter(STAFF_ID, HousekeepingTaskStatus.IN_PROGRESS, today))
                .thenReturn(List.of(shared));

            int released = service.releaseFutureTasks(STAFF_ID, UnassignedReason.TRANSFER, today);

            assertThat(released).isEqualTo(1);
            assertThat(shared.getStatus()).isEqualTo(HousekeepingTaskStatus.IN_PROGRESS);
            assertThat(shared.getAssigneeIds()).containsExactly(STAFF_2_ID);
            assertThat(shared.getUnassignedReason()).isNull();
            verifyNoInteractions(roomStatusService);
        }

        /** BR-SCH-17: chỉ ngày LỚN HƠN hôm nay — việc của chính hôm nay phải giữ nguyên. */
        @Test
        void shouldPassTodayToRepositorySoTodayTasksAreKept() {
            when(taskRepository.findOfStaffAfter(any(), any(), any())).thenReturn(List.of());

            service.releaseFutureTasks(STAFF_ID, UnassignedReason.LEAVE_APPROVED, today);

            verify(taskRepository).findOfStaffAfter(STAFF_ID, HousekeepingTaskStatus.IN_PROGRESS, today);
        }
    }

    // ── Gỡ / xóa / dời ca khi còn việc dọn — BR-HK-03 ───────────────────────

    @Nested
    class LeaveShiftDay {

        private final UUID shiftId = UUID.randomUUID();

        @BeforeEach
        void loginAsManager() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
        }

        /** Còn ca khác cùng ngày thì người đó vẫn có ngày làm việc — không cần xét việc dọn. */
        @Test
        void shouldAllowWhenStaffHasAnotherShiftThatDay() {
            when(shiftRepository.existsByStaffIdAndShiftDateAndIdNot(STAFF_ID, today, shiftId)).thenReturn(true);

            service.assertCanLeaveShiftDay(STAFF_ID, today, shiftId);

            verify(taskRepository, never()).findOfStaffOn(any(), any(), any());
        }

        /** Chỉ xét việc «Đang thực hiện»: việc «Chờ kiểm tra» người dọn đã xong phần mình. */
        @Test
        void shouldAllowWhenStaffHoldsNoInProgressTaskThatDay() {
            when(shiftRepository.existsByStaffIdAndShiftDateAndIdNot(STAFF_ID, today, shiftId)).thenReturn(false);
            when(taskRepository.findOfStaffOn(STAFF_ID, today, HousekeepingTaskStatus.IN_PROGRESS))
                .thenReturn(List.of());

            service.assertCanLeaveShiftDay(STAFF_ID, today, shiftId);

            verify(taskRepository).findOfStaffOn(STAFF_ID, today, HousekeepingTaskStatus.IN_PROGRESS);
        }

        /** Nhóm đã chốt: chặn và chỉ rõ phòng (số phòng theo thứ tự tự nhiên), không tự gỡ việc. */
        @Test
        void shouldRejectAndNameRoomsWhenStaffStillHoldsTasksThatDay() {
            when(shiftRepository.existsByStaffIdAndShiftDateAndIdNot(STAFF_ID, today, shiftId)).thenReturn(false);
            HousekeepingTask room201 = inProgressTask(STAFF_ID);
            HousekeepingTask room1005 = inProgressTask(STAFF_ID, STAFF_2_ID);
            Room otherRoom = room("1005");
            room1005.setRoomId(otherRoom.getId());
            when(taskRepository.findOfStaffOn(STAFF_ID, today, HousekeepingTaskStatus.IN_PROGRESS))
                .thenReturn(List.of(room201, room1005));
            when(roomRepository.findAllById(any())).thenReturn(List.of(otherRoom, room()));
            stubStaff(housekeepingStaff());

            assertThatThrownBy(() -> service.assertCanLeaveShiftDay(STAFF_ID, today, shiftId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Phạm Dọn Dẹp đang giữ 2 việc dọn")
                .hasMessageContaining("phòng 201, 1005")
                .hasMessageContaining("Công việc dọn phòng");
            verifyNoInteractions(roomStatusService);
        }
    }

    // ── Phạm vi dữ liệu và nội dung trả về ──────────────────────────────────

    @Nested
    class Scope {

        @Test
        void shouldThrowNotFoundForTaskOfAnotherTenant() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            UUID foreignTaskId = UUID.randomUUID();
            when(taskRepository.findByIdAndTenantId(foreignTaskId, TENANT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.unassignTask(foreignTaskId, null))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        /** Q8: ngoài Location của Manager trả 404 chứ không phải 400 — không lộ task có tồn tại. */
        @Test
        void shouldThrowNotFoundWhenManagerTouchesTaskOfAnotherLocation() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            HousekeepingTask task = inProgressTask(STAFF_ID);
            task.setLocationId(OTHER_LOCATION_ID);
            stubTask(task);

            assertThatThrownBy(() -> service.unassignTask(task.getId(), STAFF_ID))
                .isInstanceOf(ResourceNotFoundException.class);
            verifyNoInteractions(roomStatusService);
        }

        /** BR-PERM-05: nhân viên dọn không xem được việc không có mình trong nhóm. */
        @Test
        void shouldHideTaskFromStaffOutsideTeam() {
            TestAuth.loginAsStaff(TENANT_ID, LOCATION_ID, PositionType.HOUSEKEEPING);
            HousekeepingTask task = inProgressTask(STAFF_ID);
            stubTask(task);

            assertThatThrownBy(() -> service.getTask(task.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void shouldShowTaskToEveryTeamMember() {
            TestAuth.loginAs(STAFF_2_ID, Role.STAFF, TENANT_ID, LOCATION_ID, PositionType.HOUSEKEEPING);
            HousekeepingTask task = inProgressTask(STAFF_ID, STAFF_2_ID);
            stubTask(task);

            assertThat(service.getTask(task.getId()).getId()).isEqualTo(task.getId());
        }

        /** Ý 8: thẻ việc hiện "201 - Deluxe"; nhóm dọn kèm họ tên, xếp theo tên. */
        @Test
        void shouldReturnRoomTypeAndTeamNames() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            HousekeepingTask task = inProgressTask(STAFF_ID, STAFF_2_ID);
            stubTask(task);
            when(roomRepository.findAllById(any())).thenReturn(List.of(room()));
            when(roomTypeRepository.findAllById(any())).thenReturn(List.of(
                RoomType.builder().id(ROOM_TYPE_ID).tenantId(TENANT_ID).name("Deluxe").build()));
            when(userRepository.findAllById(any())).thenReturn(List.of(housekeepingStaff(), secondStaff()));

            HousekeepingTaskResponse response = service.getTask(task.getId());

            assertThat(response.getRoomNumber()).isEqualTo("201");
            assertThat(response.getRoomTypeName()).isEqualTo("Deluxe");
            assertThat(response.getAssignees()).extracting(HousekeepingTaskResponse.Assignee::fullName)
                .containsExactly("Nguyễn Thị Hoa", "Phạm Dọn Dẹp");
            assertThat(response.getAssignedStaffIds()).containsExactly(STAFF_2_ID, STAFF_ID);
        }

        @Test
        void shouldListAssignableStaffOfManagersLocationOnly() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            when(assignableStaffRepository.findAssignable(TENANT_ID, LOCATION_ID, today,
                Role.STAFF, UserStatus.ACTIVE, StaffPermission.HOUSEKEEPING))
                .thenReturn(List.of(housekeepingStaff()));

            List<AssignableStaffResponse> staff = service.getAssignableStaff(today);

            assertThat(staff).singleElement()
                .satisfies(s -> assertThat(s.getFullName()).isEqualTo("Phạm Dọn Dẹp"));
            // Location lấy từ người đang đăng nhập, không nhận từ tham số — không xem chéo được.
            verify(assignableStaffRepository).findAssignable(eq(TENANT_ID), eq(LOCATION_ID), eq(today),
                eq(Role.STAFF), eq(UserStatus.ACTIVE), eq(StaffPermission.HOUSEKEEPING));
        }
    }

    // ── Dữ liệu mẫu ─────────────────────────────────────────────────────────

    /** Stub đủ để một lệnh nghiệm thu chạy trót lọt; trả về phòng để verify lời gọi RoomStatusService. */
    private Room stubInspectReady(HousekeepingTask task) {
        stubTask(task);
        Room room = room();
        room.setStatus(RoomStatus.INSPECTION);
        when(roomRepository.findByIdAndTenantIdAndActiveTrue(ROOM_ID, TENANT_ID)).thenReturn(Optional.of(room));
        when(taskRepository.saveAndFlush(any(HousekeepingTask.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(taskRepository.save(any(HousekeepingTask.class)))
            .thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(inspectionRepository.save(any(InspectionRecord.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        return room;
    }

    /** DB sinh id lúc INSERT; mock thì phải tự gán để test đối chiếu được nextTaskId. */
    private static HousekeepingTask withId(HousekeepingTask task) {
        if (task.getId() == null) {
            task.setId(UUID.randomUUID());
        }
        return task;
    }

    private static InspectTaskRequest inspectRequest(InspectionResult result, String reason) {
        InspectTaskRequest request = new InspectTaskRequest();
        request.setResult(result);
        request.setReason(reason);
        return request;
    }

    /** Stub đủ để một lệnh giao chạy trót lọt cho cả nhóm {@code team}; trả về phòng để verify. */
    private Room stubAssignReady(HousekeepingTask task, User... team) {
        stubTask(task);
        stubLocation();
        for (User staff : team) {
            stubStaff(staff);
            when(shiftRepository.existsByStaffIdAndShiftDate(staff.getId(), today)).thenReturn(true);
        }
        Room room = room();
        when(roomRepository.findByIdAndTenantIdAndActiveTrue(ROOM_ID, TENANT_ID)).thenReturn(Optional.of(room));
        stubSave();
        return room;
    }

    private Room stubTaskAndRoom(HousekeepingTask task) {
        stubTask(task);
        Room room = room();
        when(roomRepository.findByIdAndTenantIdAndActiveTrue(ROOM_ID, TENANT_ID)).thenReturn(Optional.of(room));
        stubSave();
        return room;
    }

    private void stubSave() {
        lenient().when(taskRepository.save(any(HousekeepingTask.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void stubTask(HousekeepingTask task) {
        when(taskRepository.findByIdAndTenantId(task.getId(), TENANT_ID)).thenReturn(Optional.of(task));
    }

    private void stubLocation() {
        when(locationRepository.findByIdAndTenantId(LOCATION_ID, TENANT_ID))
            .thenReturn(Optional.of(Location.builder().id(LOCATION_ID).tenantId(TENANT_ID)
                .name("Sao Mai Hà Nội").timezone(TIMEZONE).build()));
    }

    private void stubStaff(User staff) {
        when(userRepository.findByIdAndTenantId(staff.getId(), TENANT_ID)).thenReturn(Optional.of(staff));
    }

    private static AssignTaskRequest request(LocalDate date, UUID... staffIds) {
        AssignTaskRequest request = new AssignTaskRequest();
        request.setStaffIds(List.of(staffIds));
        request.setAssignedDate(date);
        return request;
    }

    private static User housekeepingStaff() {
        return staff(STAFF_ID, "Phạm Dọn Dẹp", "dondep@test.local");
    }

    private static User secondStaff() {
        return staff(STAFF_2_ID, "Nguyễn Thị Hoa", "hoa@test.local");
    }

    private static User staff(UUID id, String fullName, String email) {
        return User.builder()
            .id(id).tenantId(TENANT_ID).locationId(LOCATION_ID).positionId(POSITION_ID)
            .permissions(EnumSet.of(StaffPermission.HOUSEKEEPING))
            .role(Role.STAFF).status(UserStatus.ACTIVE)
            .email(email).passwordHash("x").fullName(fullName).phone("0900000000")
            .build();
    }

    private static Room room() {
        return Room.builder()
            .id(ROOM_ID).tenantId(TENANT_ID).locationId(LOCATION_ID)
            .roomNumber("201").floor("2").roomTypeId(ROOM_TYPE_ID).capacity(2)
            .status(RoomStatus.DIRTY)
            .build();
    }

    private static Room room(String number) {
        return Room.builder()
            .id(UUID.randomUUID()).tenantId(TENANT_ID).locationId(LOCATION_ID)
            .roomNumber(number).floor("10").roomTypeId(ROOM_TYPE_ID).capacity(2)
            .status(RoomStatus.CLEANING)
            .build();
    }

    private HousekeepingTask unassignedTask() {
        return task(HousekeepingTaskStatus.UNASSIGNED);
    }

    /** Việc đang làm HÔM NAY của nhóm {@code team}. */
    private HousekeepingTask inProgressTask(UUID... team) {
        return task(HousekeepingTaskStatus.IN_PROGRESS, team);
    }

    private HousekeepingTask pendingInspectionTask() {
        return task(HousekeepingTaskStatus.PENDING_INSPECTION, STAFF_ID);
    }

    private HousekeepingTask task(HousekeepingTaskStatus status, UUID... team) {
        HousekeepingTask task = HousekeepingTask.builder()
            .id(UUID.randomUUID())
            .tenantId(TENANT_ID)
            .locationId(LOCATION_ID)
            .roomId(ROOM_ID)
            .status(status)
            .assignedDate(team.length == 0 ? null : today)
            .createdSource(TaskCreatedSource.CHECKOUT_AUTO)
            .build();
        task.getAssigneeIds().addAll(List.of(team));
        return task;
    }
}
