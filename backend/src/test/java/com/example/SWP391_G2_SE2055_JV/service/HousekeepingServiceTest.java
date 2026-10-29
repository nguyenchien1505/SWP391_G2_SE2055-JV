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
import com.example.SWP391_G2_SE2055_JV.enums.PositionType;
import com.example.SWP391_G2_SE2055_JV.enums.StaffPermission;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
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
    @Mock HousekeepingTaskReturnRepository returnRepository;
    @Mock RoomStatusService          roomStatusService;
    @Mock SupervisorDutyRule         supervisorDutyRule;
    @Mock CleaningWorkDayRule        cleaningWorkDayRule;

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
            User cleaner = housekeepingStaff();
            User hoa = secondStaff();
            stubStaff(cleaner);
            stubStaff(hoa);
            doNothing().when(cleaningWorkDayRule)
                .assertIsCurrentWorkDay(eq(cleaner), eq(LOCATION_ID), eq(today), any());
            doThrow(new BusinessException("Nguyễn Thị Hoa không có ca làm việc ngày này."))
                .when(cleaningWorkDayRule).assertIsCurrentWorkDay(eq(hoa), eq(LOCATION_ID), eq(today), any());

            assertThatThrownBy(() -> service.assignTask(task.getId(), request(today, STAFF_ID, STAFF_2_ID)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Nguyễn Thị Hoa không có ca làm việc");

            assertThat(task.getStatus()).isEqualTo(HousekeepingTaskStatus.UNASSIGNED);
            assertThat(task.getAssigneeIds()).isEmpty();
            verifyNoInteractions(roomStatusService);
        }

        /**
         * BR-HK-03: ngày làm phải hợp lệ với người đó — luật nằm ở {@link CleaningWorkDayRule} (test riêng
         * ở CleaningWorkDayRuleTest). Luật từ chối thì không giao và phòng đứng yên.
         */
        @Test
        void shouldPropagateWorkDayRejection() {
            HousekeepingTask task = unassignedTask();
            stubTask(task);
            stubLocation();
            User cleaner = housekeepingStaff();
            stubStaff(cleaner);
            doThrow(new BusinessException("Phạm Dọn Dẹp không có ca làm việc ngày này."))
                .when(cleaningWorkDayRule).assertIsCurrentWorkDay(eq(cleaner), eq(LOCATION_ID), eq(today), any());

            assertThatThrownBy(() -> service.assignTask(task.getId(), request(today, STAFF_ID)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ca làm việc");
            assertThat(task.getStatus()).isEqualTo(HousekeepingTaskStatus.UNASSIGNED);
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

        /** Kiểm NGƯỜI trước, ngày làm sau: người không đủ điều kiện thì không cần hỏi tới ca. */
        @Test
        void shouldRejectAssignWhenStaffNotHousekeeping() {
            HousekeepingTask task = unassignedTask();
            stubTask(task);
            User receptionist = housekeepingStaff();
            receptionist.setPermissions(EnumSet.of(StaffPermission.RECEPTION));
            stubStaff(receptionist);

            assertThatThrownBy(() -> service.assignTask(task.getId(), request(today, STAFF_ID)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Dọn dẹp");
            verifyNoInteractions(cleaningWorkDayRule);
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
            User terminated = housekeepingStaff();
            terminated.setStatus(UserStatus.TERMINATED);
            stubStaff(terminated);

            assertThatThrownBy(() -> service.assignTask(task.getId(), request(today, STAFF_ID)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đang làm việc");
        }

        /**
         * BR-SCH-17: "bây giờ" theo múi giờ KHÁCH SẠN, nên phải tra Location chứ không dùng giờ máy chủ —
         * rồi mới hỏi luật ngày làm. Các mốc ngày (đã qua, ngày mai, ca đêm) test ở CleaningWorkDayRuleTest.
         */
        @Test
        void shouldCheckWorkDayWithClockOfLocation() {
            HousekeepingTask task = unassignedTask();
            User cleaner = housekeepingStaff();
            stubAssignReady(task, cleaner);

            service.assignTask(task.getId(), request(today, STAFF_ID));

            verify(locationRepository).findByIdAndTenantId(LOCATION_ID, TENANT_ID);
            ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(cleaningWorkDayRule).assertIsCurrentWorkDay(eq(cleaner), eq(LOCATION_ID), eq(today), now.capture());
            assertThat(now.getValue().toLocalDate()).isEqualTo(today);
        }

        /**
         * Ca qua đêm (chốt 28/10/2026): luật cho phép ngày hôm qua thì việc mang ngày làm hôm qua — ngày
         * BẮT ĐẦU ca (BR-SCH-03) — và phòng vẫn sang «Đang dọn» ngay.
         */
        @Test
        void shouldAssignNightStaffWithYesterdayAsWorkDay() {
            HousekeepingTask task = unassignedTask();
            Room room = stubAssignReady(task, housekeepingStaff());

            service.assignTask(task.getId(), request(today.minusDays(1), STAFF_ID));

            assertThat(task.getAssignedDate()).isEqualTo(today.minusDays(1));
            assertThat(task.getStatus()).isEqualTo(HousekeepingTaskStatus.IN_PROGRESS);
            verify(roomStatusService).startCleaning(room, task.getId());
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

        /**
         * BR-HK-14 + BR-HK-12: việc dọn lại nằm trong danh sách chờ nhận của mọi người — nhân viên Dọn dẹp
         * cùng khách sạn đọc được lý do không đạt dù không ở trong nhóm dọn lần trước.
         */
        @Test
        void shouldLetCleanerOfSameHotelReadInspectionOutsideTeam() {
            TestAuth.loginAs(STAFF_2_ID, Role.STAFF, TENANT_ID, LOCATION_ID, PositionType.HOUSEKEEPING);
            HousekeepingTask origin = task(HousekeepingTaskStatus.COMPLETED, STAFF_ID);
            stubTask(origin);
            when(inspectionRepository.findByTenantIdAndTaskId(TENANT_ID, origin.getId()))
                .thenReturn(Optional.of(InspectionRecord.builder()
                    .tenantId(TENANT_ID).taskId(origin.getId()).roomId(ROOM_ID)
                    .inspectorId(manager.getId()).result(InspectionResult.FAIL)
                    .reason("Gương còn vết").inspectedAt(LocalDateTime.now()).build()));

            assertThat(service.getInspection(origin.getId()).getReason()).isEqualTo("Gương còn vết");
        }

        @Test
        void shouldHideInspectionFromCleanerOfAnotherHotel() {
            TestAuth.loginAs(STAFF_2_ID, Role.STAFF, TENANT_ID, OTHER_LOCATION_ID, PositionType.HOUSEKEEPING);
            HousekeepingTask origin = task(HousekeepingTaskStatus.COMPLETED, STAFF_ID);
            stubTask(origin);

            assertThatThrownBy(() -> service.getInspection(origin.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
            verifyNoInteractions(inspectionRepository);
        }

        /** Chỉ mở thêm cho người có quyền Dọn dẹp — Lễ tân ngoài nhóm vẫn không xem được. */
        @Test
        void shouldHideInspectionFromReceptionistOutsideTeam() {
            TestAuth.loginAs(STAFF_2_ID, Role.STAFF, TENANT_ID, LOCATION_ID, PositionType.RECEPTION);
            HousekeepingTask origin = task(HousekeepingTaskStatus.COMPLETED, STAFF_ID);
            stubTask(origin);

            assertThatThrownBy(() -> service.getInspection(origin.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
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

    // ── Đổi ca được duyệt — BR-SCH-26 ───────────────────────────────────────

    @Nested
    class TransferTasksOnSwap {

        private final UUID receiverId = UUID.randomUUID();

        /** Người đổi đi không còn ca nào trong ngày: việc đang làm ngày đó chuyển sang người nhận ca. */
        @Test
        void shouldMoveTasksToReceiverWhenGiverHasNoShiftLeftThatDay() {
            when(shiftRepository.existsByStaffIdAndShiftDate(STAFF_ID, today)).thenReturn(false);
            HousekeepingTask alone = inProgressTask(STAFF_ID);
            HousekeepingTask shared = inProgressTask(STAFF_ID, STAFF_2_ID);
            when(taskRepository.findOfStaffOn(STAFF_ID, today, HousekeepingTaskStatus.IN_PROGRESS))
                .thenReturn(List.of(alone, shared));

            int moved = service.transferTasksOnSwap(STAFF_ID, receiverId, today);

            assertThat(moved).isEqualTo(2);
            assertThat(alone.getAssigneeIds()).containsExactly(receiverId);
            assertThat(shared.getAssigneeIds()).containsExactlyInAnyOrder(STAFF_2_ID, receiverId);
            assertThat(alone.getStatus()).isEqualTo(HousekeepingTaskStatus.IN_PROGRESS);
            verify(taskRepository).saveAll(List.of(alone, shared));
            verifyNoInteractions(roomStatusService);
        }

        /** Người nhận đã có trong nhóm dọn thì chỉ rút tên người đổi đi. */
        @Test
        void shouldOnlyRemoveGiverWhenReceiverAlreadyInTeam() {
            when(shiftRepository.existsByStaffIdAndShiftDate(STAFF_ID, today)).thenReturn(false);
            HousekeepingTask shared = inProgressTask(STAFF_ID, STAFF_2_ID);
            when(taskRepository.findOfStaffOn(STAFF_ID, today, HousekeepingTaskStatus.IN_PROGRESS))
                .thenReturn(List.of(shared));

            service.transferTasksOnSwap(STAFF_ID, STAFF_2_ID, today);

            assertThat(shared.getAssigneeIds()).containsExactly(STAFF_2_ID);
        }

        /** Còn ca khác trong ngày (ví dụ 2 ca đổi cho nhau cùng một ngày) thì việc dọn giữ nguyên. */
        @Test
        void shouldKeepTasksWhenGiverStillHasShiftThatDay() {
            when(shiftRepository.existsByStaffIdAndShiftDate(STAFF_ID, today)).thenReturn(true);

            assertThat(service.transferTasksOnSwap(STAFF_ID, receiverId, today)).isZero();

            verify(taskRepository, never()).findOfStaffOn(any(), any(), any());
            verify(taskRepository, never()).saveAll(any());
        }
    }

    // ── Phạm vi dữ liệu và nội dung trả về ──────────────────────────────────

    @Nested
    class Scope {

        @Test
        void shouldThrowNotFoundForTaskOfAnotherTenant() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            UUID foreignTaskId = UUID.randomUUID();
            when(taskRepository.findByIdAndTenantIdForUpdate(foreignTaskId, TENANT_ID)).thenReturn(Optional.empty());

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

            List<AssignableStaffResponse> staff = service.getAssignableStaff(today, false);

            assertThat(staff).singleElement()
                .satisfies(s -> assertThat(s.getFullName()).isEqualTo("Phạm Dọn Dẹp"));
            // Location lấy từ người đang đăng nhập, không nhận từ tham số — không xem chéo được.
            verify(assignableStaffRepository).findAssignable(eq(TENANT_ID), eq(LOCATION_ID), eq(today),
                eq(Role.STAFF), eq(UserStatus.ACTIVE), eq(StaffPermission.HOUSEKEEPING));
        }

        /** BR-HK-04: cờ «Tồn đọng» lấy nguyên từ luật ngày làm — FE không tự so ngày. */
        @Test
        void shouldExposeOverdueFlagFromWorkDayRule() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            HousekeepingTask task = inProgressTask(STAFF_ID);
            task.setAssignedDate(today.minusDays(1));
            stubTask(task);
            when(cleaningWorkDayRule.overdueTaskIds(List.of(task))).thenReturn(Set.of(task.getId()));

            assertThat(service.getTask(task.getId()).isOverdue()).isTrue();
        }

        @Test
        void shouldNotMarkTaskOverdueWhenRuleSaysNo() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            HousekeepingTask task = inProgressTask(STAFF_ID);
            stubTask(task);

            assertThat(service.getTask(task.getId()).isOverdue()).isFalse();
        }
    }

    // ── Hộp phân công: người gán được NGAY — BR-HK-03, BR-SCH-03 ───────────

    @Nested
    class AssignableNow {

        @BeforeEach
        void loginAsManager() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            stubLocation();
        }

        @Test
        void shouldListTodayShiftsForToday() {
            when(assignableStaffRepository.findAssignable(TENANT_ID, LOCATION_ID, today,
                Role.STAFF, UserStatus.ACTIVE, StaffPermission.HOUSEKEEPING))
                .thenReturn(List.of(housekeepingStaff()));

            assertThat(service.getAssignableStaff(today, true)).singleElement()
                .satisfies(s -> assertThat(s.getFullName()).isEqualTo("Phạm Dọn Dẹp"));
            verify(assignableStaffRepository, never())
                .findOnOvernightShift(any(), any(), any(), any(), any(), any(), any());
        }

        /** Ca qua đêm bắt đầu hôm qua: chỉ người ca đó CHƯA hết giờ — lọc bằng giờ hiện tại của khách sạn. */
        @Test
        void shouldListOnlyStaffStillOnNightShiftForYesterday() {
            LocalDate yesterday = today.minusDays(1);
            when(assignableStaffRepository.findOnOvernightShift(eq(TENANT_ID), eq(LOCATION_ID), eq(yesterday),
                any(), eq(Role.STAFF), eq(UserStatus.ACTIVE), eq(StaffPermission.HOUSEKEEPING)))
                .thenReturn(List.of(secondStaff()));

            assertThat(service.getAssignableStaff(yesterday, true)).singleElement()
                .satisfies(s -> assertThat(s.getFullName()).isEqualTo("Nguyễn Thị Hoa"));
            verify(assignableStaffRepository, never()).findAssignable(any(), any(), any(), any(), any(), any());
        }

        /** Ngày mai hay hôm kia đều không giao được — hộp phân công không có ai để chọn. */
        @Test
        void shouldReturnNobodyForOtherDays() {
            assertThat(service.getAssignableStaff(today.plusDays(1), true)).isEmpty();
            assertThat(service.getAssignableStaff(today.minusDays(2), true)).isEmpty();
            verifyNoInteractions(assignableStaffRepository);
        }
    }

    // ── Giám sát vận hành — BR-PERM-07, BR-HK-13 ────────────────────────────

    /**
     * Giám sát làm thay Manager phần giao việc và kiểm tra phòng trong khách sạn của mình. Luật "trong
     * ngày có ca" nằm ở {@link SupervisorDutyRule} (mock ở đây, test riêng ở SupervisorDutyRuleTest).
     */
    @Nested
    class Supervisor {

        private CustomUserDetails supervisor;

        @BeforeEach
        void loginAsSupervisor() {
            supervisor = TestAuth.loginAsStaff(TENANT_ID, LOCATION_ID, PositionType.SUPERVISION);
        }

        /** Bảng điều phối: cả lịch dọn của khách sạn mình, không chỉ việc có mình trong nhóm. */
        @Test
        void shouldListWholeLocationOnCoordinationBoard() {
            when(taskRepository.search(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

            service.getTasks(HousekeepingTaskStatus.UNASSIGNED, null, null, null, null, true, PageRequest.of(0, 20));

            verify(taskRepository).search(eq(TENANT_ID), eq(LOCATION_ID), isNull(),
                eq(HousekeepingTaskStatus.UNASSIGNED), isNull(), isNull(), isNull(), isNull(), any());
        }

        /** Không bật cờ (màn «Việc của tôi»): Giám sát kiêm Dọn dẹp vẫn chỉ thấy việc của mình. */
        @Test
        void shouldListOnlyOwnTasksWithoutLocationWideFlag() {
            when(taskRepository.search(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

            service.getTasks(null, null, null, null, null, false, PageRequest.of(0, 20));

            verify(taskRepository).search(eq(TENANT_ID), isNull(), eq(supervisor.getId()),
                isNull(), isNull(), isNull(), isNull(), isNull(), any());
        }

        /** Cờ chỉ có nghĩa với Giám sát — nhân viên dọn bật lên vẫn chỉ thấy việc của mình. */
        @Test
        void shouldIgnoreLocationWideFlagForOtherStaff() {
            CustomUserDetails cleaner = TestAuth.loginAsStaff(TENANT_ID, LOCATION_ID, PositionType.HOUSEKEEPING);
            when(taskRepository.search(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

            service.getTasks(null, null, null, null, null, true, PageRequest.of(0, 20));

            verify(taskRepository).search(eq(TENANT_ID), isNull(), eq(cleaner.getId()),
                isNull(), isNull(), isNull(), isNull(), isNull(), any());
        }

        @Test
        void shouldShowTaskOfOwnLocationEvenWhenNotInTeam() {
            HousekeepingTask task = inProgressTask(STAFF_ID);
            stubTask(task);

            assertThat(service.getTask(task.getId()).getId()).isEqualTo(task.getId());
        }

        @Test
        void shouldHideTaskOfAnotherLocation() {
            HousekeepingTask task = inProgressTask(STAFF_ID);
            task.setLocationId(OTHER_LOCATION_ID);
            stubTask(task);

            assertThatThrownBy(() -> service.getTask(task.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        /** Giao như Manager sau khi qua luật "trong ngày có ca"; người giao ghi là Giám sát. */
        @Test
        void shouldAssignTaskWhenOnDuty() {
            HousekeepingTask task = unassignedTask();
            Room room = stubAssignReady(task, housekeepingStaff());

            service.assignTask(task.getId(), request(today, STAFF_ID));

            verify(supervisorDutyRule).assertOnDuty();
            assertThat(task.getStatus()).isEqualTo(HousekeepingTaskStatus.IN_PROGRESS);
            assertThat(task.getAssignedBy()).isEqualTo(supervisor.getId());
            verify(roomStatusService).startCleaning(room, task.getId());
        }

        /** BR-PERM-07 (tiếp): ngoài ngày có ca thì không giao được và không đụng tới phòng. */
        @Test
        void shouldRejectAssignWhenOffDuty() {
            HousekeepingTask task = unassignedTask();
            stubTask(task);
            doThrow(new BusinessException("Giám sát chỉ thao tác được trong ngày có ca"))
                .when(supervisorDutyRule).assertOnDuty();

            assertThatThrownBy(() -> service.assignTask(task.getId(), request(today, STAFF_ID)))
                .isInstanceOf(BusinessException.class);
            assertThat(task.getStatus()).isEqualTo(HousekeepingTaskStatus.UNASSIGNED);
            verifyNoInteractions(roomStatusService);
        }

        /** Q8: khách sạn khác trả 404 — xét trước luật ngày có ca, không lộ task có tồn tại. */
        @Test
        void shouldThrowNotFoundWhenAssigningTaskOfAnotherLocation() {
            HousekeepingTask task = unassignedTask();
            task.setLocationId(OTHER_LOCATION_ID);
            stubTask(task);

            assertThatThrownBy(() -> service.assignTask(task.getId(), request(today, STAFF_ID)))
                .isInstanceOf(ResourceNotFoundException.class);
            verifyNoInteractions(supervisorDutyRule, roomStatusService);
        }

        @Test
        void shouldInspectTaskCleanedByOthers() {
            HousekeepingTask task = pendingInspectionTask();
            Room room = stubInspectReady(task);

            service.inspectTask(task.getId(), inspectRequest(InspectionResult.PASS, null));

            verify(supervisorDutyRule).assertOnDuty();
            verify(roomStatusService).applyInspection(room, InspectionResult.PASS, null, task.getId());
            ArgumentCaptor<InspectionRecord> saved = ArgumentCaptor.forClass(InspectionRecord.class);
            verify(inspectionRepository).save(saved.capture());
            assertThat(saved.getValue().getInspectorId()).isEqualTo(supervisor.getId());
        }

        /** BR-HK-13: Giám sát kiêm Dọn dẹp không tự nghiệm thu phòng mình vừa dọn. */
        @Test
        void shouldRejectInspectingOwnCleaning() {
            HousekeepingTask task = pendingInspectionTask();
            task.getAssigneeIds().add(supervisor.getId());
            stubTask(task);

            assertThatThrownBy(() ->
                service.inspectTask(task.getId(), inspectRequest(InspectionResult.PASS, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không tự kiểm tra")
                .hasMessageNotContaining("BR-");
            assertThat(task.getStatus()).isEqualTo(HousekeepingTaskStatus.PENDING_INSPECTION);
            verifyNoInteractions(roomStatusService, inspectionRepository);
        }

        /** Q8: kiểm tra việc của khách sạn khác trả 404 — xét trước luật ngày có ca (G3, review F0). */
        @Test
        void shouldThrowNotFoundWhenInspectingTaskOfAnotherLocation() {
            HousekeepingTask task = pendingInspectionTask();
            task.setLocationId(OTHER_LOCATION_ID);
            stubTask(task);

            assertThatThrownBy(() ->
                service.inspectTask(task.getId(), inspectRequest(InspectionResult.PASS, null)))
                .isInstanceOf(ResourceNotFoundException.class);
            verifyNoInteractions(supervisorDutyRule, roomStatusService, inspectionRepository);
        }

        @Test
        void shouldListAssignableStaffOfOwnLocation() {
            when(assignableStaffRepository.findAssignable(TENANT_ID, LOCATION_ID, today,
                Role.STAFF, UserStatus.ACTIVE, StaffPermission.HOUSEKEEPING))
                .thenReturn(List.of(housekeepingStaff()));

            assertThat(service.getAssignableStaff(today, false)).singleElement()
                .satisfies(s -> assertThat(s.getFullName()).isEqualTo("Phạm Dọn Dẹp"));
        }
    }

    // ── Việc chờ nhận — BR-HK-14, BR-HK-15, BR-HK-16 (M2) ────────────────────

    @Nested
    class OpenTasks {

        @BeforeEach
        void loginAsCleaner() {
            TestAuth.loginAs(STAFF_ID, Role.STAFF, TENANT_ID, LOCATION_ID, PositionType.HOUSEKEEPING);
            stubLocation();
        }

        /** BR-HK-14: mọi việc «Chưa phân công» của khách sạn MÌNH — Quản lý không phải mở từng việc. */
        @Test
        void shouldListUnassignedTasksOfOwnLocation() {
            HousekeepingTask waiting = unassignedTask();
            stubOpenTasks(waiting);
            stubCurrentShifts(false, shift(today, true, false));
            stubHolding();

            OpenTasksResponse response = service.getOpenTasks();

            assertThat(response.getTasks()).extracting(HousekeepingTaskResponse::getId).containsExactly(waiting.getId());
            assertThat(response.isCanClaim()).isTrue();
            assertThat(response.getBlockedReason()).isNull();
            assertThat(response.getWorkDay()).isEqualTo(today);
            verify(taskRepository).search(eq(TENANT_ID), eq(LOCATION_ID), isNull(),
                eq(HousekeepingTaskStatus.UNASSIGNED), isNull(), isNull(), isNull(), isNull(), any());
        }

        /** Chưa nhận được thì vẫn thấy có bao nhiêu phòng đang chờ, kèm lý do. */
        @Test
        void shouldStillListTasksWhenClaimIsBlocked() {
            stubOpenTasks(unassignedTask());
            stubCurrentShifts(false);

            OpenTasksResponse response = service.getOpenTasks();

            assertThat(response.getTasks()).hasSize(1);
            assertThat(response.isCanClaim()).isFalse();
            assertThat(response.getBlockedReason()).contains("không có ca").doesNotContain("BR-");
            assertThat(response.getWorkDay()).isNull();
        }

        /** BR-SCH-03: đang làm ca đêm bắt đầu hôm qua thì ngày làm là hôm qua. */
        @Test
        void shouldReportWorkDayOfOvernightShift() {
            stubOpenTasks();
            stubCurrentShifts(false, shift(today.minusDays(1), true, false));
            stubHolding();

            assertThat(service.getOpenTasks().getWorkDay()).isEqualTo(today.minusDays(1));
        }

        /** Màn danh sách chỉ đọc — không khóa ca của ai. */
        @Test
        void shouldNotLockShiftsWhenOnlyListing() {
            stubOpenTasks();
            stubCurrentShifts(false);

            service.getOpenTasks();

            verify(cleaningWorkDayRule, never()).currentWorkShifts(any(), any(), any(), eq(true));
        }
    }

    // ── Tự nhận việc — BR-HK-02 (sửa), BR-HK-15, BR-HK-16, BR-HK-18 (M2) ─────

    @Nested
    class Claim {

        @BeforeEach
        void loginAsCleaner() {
            TestAuth.loginAs(STAFF_ID, Role.STAFF, TENANT_ID, LOCATION_ID, PositionType.HOUSEKEEPING);
        }

        /** Nhận = giao: việc sang «Đang thực hiện», phòng sang «Đang dọn» ngay (BR-ROOM-02 bản sửa). */
        @Test
        void shouldClaimAndMoveRoomToCleaning() {
            HousekeepingTask task = unassignedTask();
            Room room = stubClaimReady(task, shift(today, true, false));

            HousekeepingTaskResponse response = service.claimTask(task.getId());

            assertThat(response.getStatus()).isEqualTo(HousekeepingTaskStatus.IN_PROGRESS);
            assertThat(task.getAssigneeIds()).containsExactly(STAFF_ID);
            assertThat(task.getAssignedDate()).isEqualTo(today);
            verify(roomStatusService).startCleaning(room, task.getId());
        }

        /** BR-HK-12 (sửa): lưu cách giao là tự nhận, người giao là chính người nhận. */
        @Test
        void shouldRecordSelfClaimAndClaimerAsAssigner() {
            HousekeepingTask task = unassignedTask();
            task.setUnassignedReason(UnassignedReason.MANAGER_MANUAL);   // từng bị Quản lý gỡ người
            stubClaimReady(task, shift(today, true, false));

            service.claimTask(task.getId());

            assertThat(task.getAssignmentSource()).isEqualTo(TaskAssignmentSource.SELF_CLAIM);
            assertThat(task.getAssignedBy()).isEqualTo(STAFF_ID);
            assertThat(task.getAssignedAt()).isNotNull();
            assertThat(task.getUnassignedReason()).isNull();
        }

        /** Ca đêm bắt đầu hôm qua còn đang làm: ngày làm của việc là hôm qua (BR-SCH-03). */
        @Test
        void shouldUseWorkDayOfOvernightShiftAsAssignedDate() {
            HousekeepingTask task = unassignedTask();
            stubClaimReady(task, shift(today.minusDays(1), true, false));

            service.claimTask(task.getId());

            assertThat(task.getAssignedDate()).isEqualTo(today.minusDays(1));
        }

        /** BR-HK-18: người tới sau không tự vào nhóm của việc đã có người. */
        @Test
        void shouldRejectWhenTaskAlreadyTaken() {
            HousekeepingTask task = inProgressTask(STAFF_2_ID);
            stubTask(task);
            stubRoom();

            assertThatThrownBy(() -> service.claimTask(task.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Phòng 201 vừa có người nhận")
                .hasMessageNotContaining("BR-");
            assertThat(task.getAssigneeIds()).containsExactly(STAFF_2_ID);
            verifyNoInteractions(roomStatusService, cleaningWorkDayRule);
        }

        @Test
        void shouldRejectWhenTaskPendingInspection() {
            HousekeepingTask task = pendingInspectionTask();
            stubTask(task);
            stubRoom();

            assertThatThrownBy(() -> service.claimTask(task.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("vừa có người nhận");
        }

        @Test
        void shouldRejectWhenTaskCancelled() {
            HousekeepingTask task = task(HousekeepingTaskStatus.CANCELLED);
            stubTask(task);
            stubRoom();

            assertThatThrownBy(() -> service.claimTask(task.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã bị hủy")
                .hasMessageNotContaining("BR-");
            verifyNoInteractions(roomStatusService);
        }

        @Test
        void shouldRejectWithoutShiftToday() {
            HousekeepingTask task = unassignedTask();
            stubTaskRoomAndLocation(task);
            stubCurrentShifts(true);

            assertClaimBlocked(task, "không có ca");
        }

        /** BR-HK-15: có ca mà chưa vào ca thì chưa nhận được. */
        @Test
        void shouldRejectBeforeCheckIn() {
            HousekeepingTask task = unassignedTask();
            stubTaskRoomAndLocation(task);
            stubCurrentShifts(true, shift(today, false, false));

            assertClaimBlocked(task, "«Vào ca»");
        }

        @Test
        void shouldRejectAfterCheckOut() {
            HousekeepingTask task = unassignedTask();
            stubTaskRoomAndLocation(task);
            stubCurrentShifts(true, shift(today, true, true));

            assertClaimBlocked(task, "đã kết thúc ca");
        }

        /** Ngày hai ca: ca sáng đã kết thúc, ca chiều đang làm — vẫn nhận được. */
        @Test
        void shouldAllowWhenSecondShiftOfDayIsCheckedIn() {
            HousekeepingTask task = unassignedTask();
            stubClaimReady(task, shift(today, true, true), shift(today, true, false));

            service.claimTask(task.getId());

            assertThat(task.getAssignmentSource()).isEqualTo(TaskAssignmentSource.SELF_CLAIM);
        }

        /** BR-HK-16: đang giữ việc «Đang thực hiện» (kể cả việc được gán tay) thì chưa nhận thêm. */
        @Test
        void shouldRejectWhileHoldingTask() {
            HousekeepingTask task = unassignedTask();
            stubTaskRoomAndLocation(task);
            stubCurrentShifts(true, shift(today, true, false));
            HousekeepingTask holding = inProgressTask(STAFF_ID);
            Room room305 = room("305");
            holding.setRoomId(room305.getId());
            holding.setAssignmentSource(TaskAssignmentSource.MANAGER);
            stubHolding(holding);
            when(roomRepository.findAllById(Set.of(room305.getId()))).thenReturn(List.of(room305));

            assertClaimBlocked(task, "Bạn đang dọn phòng 305");
        }

        /** Lưới chung với gán tay: người vừa bị bỏ quyền Dọn dẹp thì bị chặn theo dữ liệu DB. */
        @Test
        void shouldRejectWhenHousekeepingPermissionRemovedInDb() {
            HousekeepingTask task = unassignedTask();
            stubTaskRoomAndLocation(task);
            stubCurrentShifts(true, shift(today, true, false));
            stubHolding();
            User demoted = housekeepingStaff();
            demoted.setPermissions(EnumSet.of(StaffPermission.RECEPTION));
            stubStaff(demoted);

            assertClaimBlocked(task, "chưa được cấp quyền Dọn dẹp");
        }

        /** Q8: việc của khách sạn khác trả 404 — không lộ việc có tồn tại. */
        @Test
        void shouldThrowNotFoundForTaskOfAnotherLocation() {
            HousekeepingTask task = unassignedTask();
            task.setLocationId(OTHER_LOCATION_ID);
            stubTask(task);

            assertThatThrownBy(() -> service.claimTask(task.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
            verifyNoInteractions(roomStatusService, cleaningWorkDayRule);
        }

        @Test
        void shouldThrowNotFoundForTaskOfAnotherTenant() {
            UUID foreignTaskId = UUID.randomUUID();
            when(taskRepository.findByIdAndTenantIdForUpdate(foreignTaskId, TENANT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.claimTask(foreignTaskId))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        /** Chống bấm đồng thời: tải việc bằng bản KHÓA dòng, và là câu đầu tiên (F8 §3). */
        @Test
        void shouldLoadTaskWithLockFirst() {
            HousekeepingTask task = unassignedTask();
            stubClaimReady(task, shift(today, true, false));

            service.claimTask(task.getId());

            InOrder order = inOrder(taskRepository, roomRepository, cleaningWorkDayRule);
            order.verify(taskRepository).findByIdAndTenantIdForUpdate(task.getId(), TENANT_ID);
            order.verify(roomRepository).findByIdAndTenantIdAndActiveTrue(ROOM_ID, TENANT_ID);
            order.verify(cleaningWorkDayRule).currentWorkShifts(eq(STAFF_ID), eq(LOCATION_ID), any(), eq(true));
            verify(taskRepository, never()).findByIdAndTenantId(any(), any());
        }

        /** Một người bấm hai việc cùng lúc: khóa ca của mình TRƯỚC khi đếm việc đang làm (F8 §3.2). */
        @Test
        void shouldLockOwnShiftBeforeCountingHeldTasks() {
            HousekeepingTask task = unassignedTask();
            stubClaimReady(task, shift(today, true, false));

            service.claimTask(task.getId());

            InOrder order = inOrder(cleaningWorkDayRule, taskRepository);
            order.verify(cleaningWorkDayRule).currentWorkShifts(eq(STAFF_ID), eq(LOCATION_ID), any(), eq(true));
            order.verify(taskRepository).search(eq(TENANT_ID), isNull(), eq(STAFF_ID),
                eq(HousekeepingTaskStatus.IN_PROGRESS), isNull(), isNull(), isNull(), isNull(), any());
        }

        private void assertClaimBlocked(HousekeepingTask task, String reason) {
            assertThatThrownBy(() -> service.claimTask(task.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(reason)
                .hasMessageNotContaining("BR-");
            assertThat(task.getStatus()).isEqualTo(HousekeepingTaskStatus.UNASSIGNED);
            assertThat(task.getAssigneeIds()).isEmpty();
            verifyNoInteractions(roomStatusService);
        }
    }

    // ── Trả lại việc đã tự nhận — BR-HK-17 ──────────────────────────────────

    @Nested
    class ReturnTask {

        @BeforeEach
        void loginAsCleaner() {
            TestAuth.loginAs(STAFF_ID, Role.STAFF, TENANT_ID, LOCATION_ID, PositionType.HOUSEKEEPING);
        }

        /** Dọn một mình: việc về hàng chờ, phòng về «Chờ dọn» kèm lý do người trả nhập (BR-ROOM-02 bản sửa). */
        @Test
        void shouldReleaseTaskAndRevertRoomWhenSoleClaimerReturns() {
            HousekeepingTask task = claimedTask(STAFF_ID, STAFF_ID);
            Room room = stubTaskAndRoom(task);

            HousekeepingTaskResponse response =
                service.returnTask(task.getId(), returnRequest("Đau lưng, không dọn tiếp được"));

            assertThat(response.getStatus()).isEqualTo(HousekeepingTaskStatus.UNASSIGNED);
            assertThat(task.getAssigneeIds()).isEmpty();
            assertThat(task.getUnassignedReason()).isEqualTo(UnassignedReason.SELF_RETURNED);
            assertThat(task.getAssignmentSource()).isNull();
            assertThat(task.getAssignedBy()).isNull();
            assertThat(task.getAssignedDate()).isNull();
            verify(roomStatusService).returnToQueue(room, task.getId(), "Đau lưng, không dọn tiếp được");

            HousekeepingTaskReturn saved = capturedReturn();
            assertThat(saved.getTenantId()).isEqualTo(TENANT_ID);
            assertThat(saved.getTaskId()).isEqualTo(task.getId());
            assertThat(saved.getStaffId()).isEqualTo(STAFF_ID);
            assertThat(saved.getReason()).isEqualTo("Đau lưng, không dọn tiếp được");
            assertThat(saved.getReturnedAt()).isNotNull();
            assertThat(saved.isTaskReleased()).isTrue();
        }

        /** Nhóm còn người Quản lý thêm vào: chỉ rút tên người trả — việc, cách giao và phòng đứng yên. */
        @Test
        void shouldOnlyRemoveReturnerWhenTeamHasOthers() {
            HousekeepingTask task = claimedTask(STAFF_ID, STAFF_ID, STAFF_2_ID);
            stubTask(task);
            stubSave();

            service.returnTask(task.getId(), returnRequest("Phải xuống hỗ trợ lễ tân"));

            assertThat(task.getStatus()).isEqualTo(HousekeepingTaskStatus.IN_PROGRESS);
            assertThat(task.getAssigneeIds()).containsExactly(STAFF_2_ID);
            assertThat(task.getAssignmentSource()).isEqualTo(TaskAssignmentSource.SELF_CLAIM);
            assertThat(task.getAssignedBy()).isEqualTo(STAFF_ID);
            assertThat(task.getUnassignedReason()).isNull();
            assertThat(capturedReturn().isTaskReleased()).isFalse();
            verifyNoInteractions(roomStatusService);
        }

        /** Lý do bắt buộc — lưới sau {@code @NotBlank} của controller; không ghi gì cả. */
        @Test
        void shouldRequireReason() {
            HousekeepingTask task = claimedTask(STAFF_ID, STAFF_ID);
            stubTask(task);

            for (String blank : new String[] {null, "", "   "}) {
                assertThatThrownBy(() -> service.returnTask(task.getId(), returnRequest(blank)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Nhập lý do trả lại việc")
                    .hasMessageNotContaining("BR-");
            }
            assertThat(task.getAssigneeIds()).containsExactly(STAFF_ID);
            verifyNoInteractions(roomStatusService, returnRepository);
        }

        @Test
        void shouldTrimReason() {
            HousekeepingTask task = claimedTask(STAFF_ID, STAFF_ID);
            Room room = stubTaskAndRoom(task);

            service.returnTask(task.getId(), returnRequest("  Thiếu hóa chất tẩy rửa  "));

            verify(roomStatusService).returnToQueue(room, task.getId(), "Thiếu hóa chất tẩy rửa");
            assertThat(capturedReturn().getReason()).isEqualTo("Thiếu hóa chất tẩy rửa");
        }

        @Test
        void shouldRejectReturnOfManagerAssignedTask() {
            HousekeepingTask task = inProgressTask(STAFF_ID);
            task.setAssignmentSource(TaskAssignmentSource.MANAGER);
            task.setAssignedBy(UUID.randomUUID());

            assertReturnRejected(task, "Chỉ trả lại được việc bạn tự nhận");
        }

        @Test
        void shouldRejectReturnOfSupervisionAssignedTask() {
            HousekeepingTask task = inProgressTask(STAFF_ID);
            task.setAssignmentSource(TaskAssignmentSource.SUPERVISION);
            task.setAssignedBy(UUID.randomUUID());

            assertReturnRejected(task, "Chỉ trả lại được việc bạn tự nhận");
        }

        /** Hoa tự nhận, Quản lý thêm mình vào nhóm: mình không trả lại được — người giao là Hoa. */
        @Test
        void shouldRejectReturnByTeammateAddedLater() {
            HousekeepingTask task = claimedTask(STAFF_2_ID, STAFF_2_ID, STAFF_ID);

            assertReturnRejected(task, "Chỉ trả lại được việc bạn tự nhận");
        }

        @Test
        void shouldRejectReturnAfterCompletion() {
            HousekeepingTask task = claimedTask(STAFF_ID, STAFF_ID);
            task.setStatus(HousekeepingTaskStatus.PENDING_INSPECTION);

            assertReturnRejected(task, "đã báo hoàn thành");
        }

        /** Phòng vừa bị khóa (BR-HK-09): việc đã hủy, nhóm vẫn còn tên. */
        @Test
        void shouldRejectReturnOfCancelledTask() {
            HousekeepingTask task = claimedTask(STAFF_ID, STAFF_ID);
            task.setStatus(HousekeepingTaskStatus.CANCELLED);

            assertReturnRejected(task, "đã bị hủy");
        }

        /** Không có mình trong nhóm: 404 như xem việc — không lộ việc có tồn tại. */
        @Test
        void shouldThrowNotFoundWhenNotInTeam() {
            HousekeepingTask task = claimedTask(STAFF_2_ID, STAFF_2_ID);
            stubTask(task);

            assertThatThrownBy(() -> service.returnTask(task.getId(), returnRequest("Bận việc khác")))
                .isInstanceOf(ResourceNotFoundException.class);
            assertThat(task.getAssigneeIds()).containsExactly(STAFF_2_ID);
            verifyNoInteractions(roomStatusService, returnRepository);
        }

        @Test
        void shouldThrowNotFoundForTaskOfAnotherTenant() {
            UUID foreignTaskId = UUID.randomUUID();
            when(taskRepository.findByIdAndTenantIdForUpdate(foreignTaskId, TENANT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.returnTask(foreignTaskId, returnRequest("Bận việc khác")))
                .isInstanceOf(ResourceNotFoundException.class);
            verifyNoInteractions(roomStatusService, returnRepository);
        }

        /** Trả lại không đòi đang trong ca: việc tồn đọng từ hôm qua vẫn trả được. */
        @Test
        void shouldAllowReturnOfOverdueTaskWithoutCheckingShift() {
            HousekeepingTask task = claimedTask(STAFF_ID, STAFF_ID);
            task.setAssignedDate(today.minusDays(1));
            stubTaskAndRoom(task);

            service.returnTask(task.getId(), returnRequest("Hết ca, không kịp dọn"));

            assertThat(task.getStatus()).isEqualTo(HousekeepingTaskStatus.UNASSIGNED);
            verify(cleaningWorkDayRule, never()).currentWorkShifts(any(), any(), any(), anyBoolean());
            verifyNoInteractions(shiftRepository, locationRepository);
        }

        private void assertReturnRejected(HousekeepingTask task, String message) {
            stubTask(task);
            Set<UUID> teamBefore = Set.copyOf(task.getAssigneeIds());

            assertThatThrownBy(() -> service.returnTask(task.getId(), returnRequest("Bận việc khác")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(message)
                .hasMessageNotContaining("BR-");
            assertThat(task.getAssigneeIds()).containsExactlyInAnyOrderElementsOf(teamBefore);
            verifyNoInteractions(roomStatusService, returnRepository);
        }

        private HousekeepingTaskReturn capturedReturn() {
            ArgumentCaptor<HousekeepingTaskReturn> captor = ArgumentCaptor.forClass(HousekeepingTaskReturn.class);
            verify(returnRepository).save(captor.capture());
            return captor.getValue();
        }
    }

    // ── Lịch sử trả lại — BR-HK-17 ──────────────────────────────────────────

    @Nested
    class TaskReturns {

        @Test
        void shouldListNewestFirstWithStaffNames() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            HousekeepingTask task = unassignedTask();
            stubTask(task);
            HousekeepingTaskReturn newer = taskReturn(task, STAFF_2_ID, "Phải đi họp", true);
            HousekeepingTaskReturn older = taskReturn(task, STAFF_ID, "Thiếu hóa chất", true);
            when(returnRepository.findByTenantIdAndTaskIdOrderByReturnedAtDesc(TENANT_ID, task.getId()))
                .thenReturn(List.of(newer, older));
            when(userRepository.findAllById(Set.of(STAFF_ID, STAFF_2_ID)))
                .thenReturn(List.of(housekeepingStaff(), secondStaff()));

            List<TaskReturnResponse> returns = service.getTaskReturns(task.getId());

            assertThat(returns).extracting(TaskReturnResponse::getReason).containsExactly("Phải đi họp", "Thiếu hóa chất");
            assertThat(returns).extracting(TaskReturnResponse::getStaffName)
                .containsExactly("Nguyễn Thị Hoa", "Phạm Dọn Dẹp");
        }

        /** Q8: Quản lý khách sạn khác — 404, không đọc lịch sử. */
        @Test
        void shouldThrowNotFoundForManagerOfAnotherLocation() {
            TestAuth.loginAsManager(TENANT_ID, OTHER_LOCATION_ID);
            HousekeepingTask task = unassignedTask();
            stubTask(task);

            assertThatThrownBy(() -> service.getTaskReturns(task.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
            verifyNoInteractions(returnRepository);
        }

        /** BR-PERM-05: nhân viên chỉ xem việc đang có mình trong nhóm. */
        @Test
        void shouldThrowNotFoundForStaffNotInTeam() {
            TestAuth.loginAs(STAFF_ID, Role.STAFF, TENANT_ID, LOCATION_ID, PositionType.HOUSEKEEPING);
            HousekeepingTask task = inProgressTask(STAFF_2_ID);
            stubTask(task);

            assertThatThrownBy(() -> service.getTaskReturns(task.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
            verifyNoInteractions(returnRepository);
        }

        /** Thẻ việc biết đã bị trả mấy lần — một câu đếm cho cả trang. */
        @Test
        void shouldReportReturnCountOnTask() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            HousekeepingTask task = unassignedTask();
            stubTask(task);
            when(returnRepository.countGroupedByTask(Set.of(task.getId())))
                .thenReturn(List.of(returnCount(task.getId(), 2)));

            assertThat(service.getTask(task.getId()).getReturnCount()).isEqualTo(2);
        }

        @Test
        void shouldReportZeroReturnsWhenNeverReturned() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            HousekeepingTask task = unassignedTask();
            stubTask(task);

            assertThat(service.getTask(task.getId()).getReturnCount()).isZero();
        }

        private HousekeepingTaskReturn taskReturn(HousekeepingTask task, UUID staffId, String reason,
                                                  boolean released) {
            return HousekeepingTaskReturn.builder()
                .id(UUID.randomUUID()).tenantId(TENANT_ID).taskId(task.getId()).staffId(staffId)
                .reason(reason).returnedAt(LocalDateTime.now()).taskReleased(released)
                .build();
        }

        private TaskReturnCount returnCount(UUID taskId, long total) {
            return new TaskReturnCount() {
                @Override public UUID getTaskId() { return taskId; }
                @Override public long getTotal() { return total; }
            };
        }
    }

    // ── Cách giao việc — BR-HK-12 (sửa M2) ──────────────────────────────────

    @Nested
    class AssignmentSource {

        @Test
        void shouldRecordManagerSourceWhenManagerAssigns() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            HousekeepingTask task = unassignedTask();
            stubAssignReady(task, housekeepingStaff());

            service.assignTask(task.getId(), request(today, STAFF_ID));

            assertThat(task.getAssignmentSource()).isEqualTo(TaskAssignmentSource.MANAGER);
        }

        @Test
        void shouldRecordSupervisionSourceWhenSupervisorAssigns() {
            CustomUserDetails supervisor = TestAuth.loginAsStaff(TENANT_ID, LOCATION_ID, PositionType.SUPERVISION);
            HousekeepingTask task = unassignedTask();
            stubAssignReady(task, housekeepingStaff());

            service.assignTask(task.getId(), request(today, STAFF_ID));

            assertThat(task.getAssignmentSource()).isEqualTo(TaskAssignmentSource.SUPERVISION);
            assertThat(task.getAssignedBy()).isEqualTo(supervisor.getId());
        }

        /** Thêm người vào việc đang làm không đổi cách giao — đó là cách việc được BẮT ĐẦU. */
        @Test
        void shouldKeepSourceAndAssignerWhenAddingPeople() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            HousekeepingTask task = inProgressTask(STAFF_ID);
            task.setAssignmentSource(TaskAssignmentSource.SELF_CLAIM);
            task.setAssignedBy(STAFF_ID);
            stubTask(task);
            stubLocation();
            stubStaff(secondStaff());
            stubSave();

            service.assignTask(task.getId(), request(today, STAFF_2_ID));

            assertThat(task.getAssignmentSource()).isEqualTo(TaskAssignmentSource.SELF_CLAIM);
            assertThat(task.getAssignedBy()).isEqualTo(STAFF_ID);
        }

        @Test
        void shouldClearSourceWhenLastPersonUnassigned() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            HousekeepingTask task = inProgressTask(STAFF_ID);
            task.setAssignmentSource(TaskAssignmentSource.SELF_CLAIM);
            stubTaskAndRoom(task);

            service.unassignTask(task.getId(), STAFF_ID);

            assertThat(task.getStatus()).isEqualTo(HousekeepingTaskStatus.UNASSIGNED);
            assertThat(task.getAssignmentSource()).isNull();
        }

        @Test
        void shouldClearSourceWhenReleasedByTermination() {
            HousekeepingTask task = inProgressTask(STAFF_ID);
            task.setAssignedDate(today.plusDays(1));
            task.setAssignmentSource(TaskAssignmentSource.MANAGER);
            when(taskRepository.findOfStaffAfter(STAFF_ID, HousekeepingTaskStatus.IN_PROGRESS, today))
                .thenReturn(List.of(task));
            stubRoom();

            service.releaseFutureTasks(STAFF_ID, UnassignedReason.TERMINATION, today);

            assertThat(task.getAssignmentSource()).isNull();
        }
    }

    // ── Mọi đường GHI việc dọn tải việc có khóa dòng (F8 §2.4e) ────────────

    @Nested
    class WriteLock {

        @BeforeEach
        void loginAsManager() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
        }

        @Test
        void shouldLockTaskOnAssign() {
            HousekeepingTask task = unassignedTask();
            stubAssignReady(task, housekeepingStaff());

            service.assignTask(task.getId(), request(today, STAFF_ID));

            assertLoadedWithLock(task);
        }

        @Test
        void shouldLockTaskOnUnassign() {
            HousekeepingTask task = inProgressTask(STAFF_ID);
            stubTaskAndRoom(task);

            service.unassignTask(task.getId(), null);

            assertLoadedWithLock(task);
        }

        @Test
        void shouldLockTaskOnComplete() {
            TestAuth.loginAs(STAFF_ID, Role.STAFF, TENANT_ID, LOCATION_ID, PositionType.HOUSEKEEPING);
            HousekeepingTask task = inProgressTask(STAFF_ID);
            stubTaskAndRoom(task);

            service.completeTask(task.getId());

            assertLoadedWithLock(task);
        }

        @Test
        void shouldLockTaskOnInspect() {
            HousekeepingTask task = pendingInspectionTask();
            stubInspectReady(task);

            service.inspectTask(task.getId(), inspectRequest(InspectionResult.PASS, null));

            assertLoadedWithLock(task);
        }

        /** Đồng đội có thể đang bấm «Hoàn thành» đúng lúc người tự nhận trả lại việc. */
        @Test
        void shouldLockTaskOnReturn() {
            TestAuth.loginAs(STAFF_ID, Role.STAFF, TENANT_ID, LOCATION_ID, PositionType.HOUSEKEEPING);
            HousekeepingTask task = claimedTask(STAFF_ID, STAFF_ID);
            stubTaskAndRoom(task);

            service.returnTask(task.getId(), returnRequest("Bận việc khác"));

            assertLoadedWithLock(task);
        }

        private void assertLoadedWithLock(HousekeepingTask task) {
            verify(taskRepository).findByIdAndTenantIdForUpdate(task.getId(), TENANT_ID);
            verify(taskRepository, never()).findByIdAndTenantId(any(), any());
        }
    }

    // ── Dữ liệu mẫu ─────────────────────────────────────────────────────────

    /** Stub đủ để một lệnh tự nhận chạy trót lọt với ca {@code shifts}; trả về phòng để verify. */
    private Room stubClaimReady(HousekeepingTask task, Shift... shifts) {
        Room room = stubTaskRoomAndLocation(task);
        stubCurrentShifts(true, shifts);
        stubHolding();
        stubStaff(housekeepingStaff());
        stubSave();
        return room;
    }

    private Room stubTaskRoomAndLocation(HousekeepingTask task) {
        stubTask(task);
        stubLocation();
        return stubRoom();
    }

    private Room stubRoom() {
        Room room = room();
        when(roomRepository.findByIdAndTenantIdAndActiveTrue(ROOM_ID, TENANT_ID)).thenReturn(Optional.of(room));
        return room;
    }

    /** Ca thuộc ngày làm hiện tại của người dọn chính ({@code STAFF_ID}) — luật ngày làm được mock. */
    private void stubCurrentShifts(boolean lock, Shift... shifts) {
        when(cleaningWorkDayRule.currentWorkShifts(eq(STAFF_ID), eq(LOCATION_ID), any(), eq(lock)))
            .thenReturn(List.of(shifts));
    }

    /** Việc «Đang thực hiện» người dọn chính đang giữ — BR-HK-16. */
    private void stubHolding(HousekeepingTask... tasks) {
        when(taskRepository.search(eq(TENANT_ID), isNull(), eq(STAFF_ID), eq(HousekeepingTaskStatus.IN_PROGRESS),
            isNull(), isNull(), isNull(), isNull(), any()))
            .thenReturn(new PageImpl<>(List.of(tasks)));
    }

    private void stubOpenTasks(HousekeepingTask... tasks) {
        when(taskRepository.search(eq(TENANT_ID), eq(LOCATION_ID), isNull(), eq(HousekeepingTaskStatus.UNASSIGNED),
            isNull(), isNull(), isNull(), isNull(), any()))
            .thenReturn(new PageImpl<>(List.of(tasks)));
    }

    /** Ca của người dọn chính ngày {@code date}: đã vào ca chưa, đã kết thúc ca chưa. */
    private static Shift shift(LocalDate date, boolean checkedIn, boolean checkedOut) {
        return Shift.builder()
            .id(UUID.randomUUID()).tenantId(TENANT_ID).locationId(LOCATION_ID).staffId(STAFF_ID).shiftDate(date)
            .startTime(java.time.LocalTime.of(6, 0)).endTime(java.time.LocalTime.of(14, 0))
            .durationHours(new java.math.BigDecimal("8.00"))
            .checkInAt(checkedIn ? date.atTime(6, 0) : null)
            .checkOutAt(checkedOut ? date.atTime(14, 0) : null)
            .build();
    }

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

    /** Việc đang làm do {@code claimer} tự nhận; nhóm hiện tại là {@code team} (có thể đã được thêm người). */
    private HousekeepingTask claimedTask(UUID claimer, UUID... team) {
        HousekeepingTask task = inProgressTask(team);
        task.setAssignmentSource(TaskAssignmentSource.SELF_CLAIM);
        task.setAssignedBy(claimer);
        return task;
    }

    private static ReturnTaskRequest returnRequest(String reason) {
        ReturnTaskRequest request = new ReturnTaskRequest();
        request.setReason(reason);
        return request;
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
        // Luật ngày làm (mock) mặc định cho qua — các mốc ngày test riêng ở CleaningWorkDayRuleTest.
        for (User staff : team) {
            stubStaff(staff);
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

    /**
     * Đường đọc tải việc bằng {@code findByIdAndTenantId}, đường ghi bằng bản KHÓA dòng — stub cả hai
     * (lenient: mỗi test chỉ đi một đường). Đường ghi có đúng là dùng bản khóa không thì test riêng ở
     * {@link WriteLock}.
     */
    private void stubTask(HousekeepingTask task) {
        lenient().when(taskRepository.findByIdAndTenantId(task.getId(), TENANT_ID)).thenReturn(Optional.of(task));
        lenient().when(taskRepository.findByIdAndTenantIdForUpdate(task.getId(), TENANT_ID))
            .thenReturn(Optional.of(task));
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
