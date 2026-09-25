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
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.HousekeepingTaskStatus;
import com.example.SWP391_G2_SE2055_JV.enums.HousekeepingTaskType;
import com.example.SWP391_G2_SE2055_JV.enums.InspectionResult;
import com.example.SWP391_G2_SE2055_JV.enums.PositionType;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import com.example.SWP391_G2_SE2055_JV.enums.TaskCancelReason;
import com.example.SWP391_G2_SE2055_JV.enums.TaskCreatedSource;
import com.example.SWP391_G2_SE2055_JV.enums.UnassignedReason;
import com.example.SWP391_G2_SE2055_JV.enums.UserStatus;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.AssignableStaffRepository;
import com.example.SWP391_G2_SE2055_JV.repository.HousekeepingTaskRepository;
import com.example.SWP391_G2_SE2055_JV.repository.InspectionRecordRepository;
import com.example.SWP391_G2_SE2055_JV.repository.LocationRepository;
import com.example.SWP391_G2_SE2055_JV.repository.PositionRepository;
import com.example.SWP391_G2_SE2055_JV.repository.RoomRepository;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
 * F5 — giai đoạn 2 của Housekeeping: BR-HK-02, BR-HK-03, BR-HK-05, BR-HK-06, BR-HK-07,
 * BR-PERM-05, và hệ quả lên trạng thái phòng (BR-ROOM-02).
 *
 * <p>Trọng tâm là RANH GIỚI GIỮA HAI LOẠI TASK: task dọn sau check-out kéo theo trạng thái phòng,
 * task dọn hằng ngày thì tuyệt đối không. {@link RoomStatusService} được mock để kiểm đúng điều
 * đó bằng {@code verify} / {@code verifyNoInteractions} — luật đổi trạng thái phòng đã test riêng
 * ở {@code RoomStatusServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class HousekeepingServiceTest {

    private static final UUID TENANT_ID         = UUID.randomUUID();
    private static final UUID LOCATION_ID       = UUID.randomUUID();
    private static final UUID OTHER_LOCATION_ID = UUID.randomUUID();
    private static final UUID ROOM_ID           = UUID.randomUUID();
    private static final UUID STAFF_ID          = UUID.randomUUID();
    private static final UUID POSITION_ID       = UUID.randomUUID();
    private static final UUID EXTRA_POSITION_ID = UUID.randomUUID();

    /** Múi giờ của khách sạn mẫu; "hôm nay" trong test luôn tính theo đúng mốc này (BR-SCH-17). */
    private static final String TIMEZONE = "Asia/Ho_Chi_Minh";

    @Mock HousekeepingTaskRepository taskRepository;
    @Mock UserRepository             userRepository;
    @Mock PositionRepository         positionRepository;
    @Mock ShiftRepository            shiftRepository;
    @Mock RoomRepository             roomRepository;
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

    // ── Phân công task — BR-HK-02, BR-HK-03 ─────────────────────────────────

    @Nested
    class AssignTask {

        @BeforeEach
        void loginAsManager() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
        }

        /** BR-ROOM-02: gán người cho việc dọn sau check-out là phòng sang «Đang dọn» ngay. */
        @Test
        void shouldMoveRoomToCleaningWhenAssigningCheckoutTask() {
            HousekeepingTask task = unassignedTask(HousekeepingTaskType.CHECKOUT);
            Room room = stubAssignReady(task);

            HousekeepingTaskResponse response = service.assignTask(task.getId(), request(today));

            assertThat(response.getStatus()).isEqualTo(HousekeepingTaskStatus.IN_PROGRESS);
            assertThat(task.getAssignedStaffId()).isEqualTo(STAFF_ID);
            verify(roomStatusService).startCleaning(room, task.getId());
        }

        /** BR-HK-05: dọn hằng ngày thì khách vẫn đang ở — phòng KHÔNG được đổi trạng thái. */
        @Test
        void shouldNotTouchRoomWhenAssigningStayoverTask() {
            HousekeepingTask task = unassignedTask(HousekeepingTaskType.STAYOVER);
            stubAssignReady(task);

            service.assignTask(task.getId(), request(today));

            assertThat(task.getStatus()).isEqualTo(HousekeepingTaskStatus.IN_PROGRESS);
            verifyNoInteractions(roomStatusService);
        }

        /** BR-HK-03: có ca trong ngày là điều kiện bắt buộc, không cần khớp khung giờ. */
        @Test
        void shouldRejectAssignWhenStaffHasNoShiftThatDay() {
            HousekeepingTask task = unassignedTask(HousekeepingTaskType.CHECKOUT);
            stubTask(task);
            stubLocation();
            stubStaff(housekeepingStaff());
            when(shiftRepository.existsByStaffIdAndShiftDate(STAFF_ID, today)).thenReturn(false);

            assertThatThrownBy(() -> service.assignTask(task.getId(), request(today)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ca làm việc")
                .hasMessageNotContaining("BR-");
            verifyNoInteractions(roomStatusService);
        }

        /** BR-HK-02: KHÔNG giới hạn số task mỗi người — không được có truy vấn đếm nào. */
        @Test
        void shouldNotLimitNumberOfTasksPerStaff() {
            HousekeepingTask task = unassignedTask(HousekeepingTaskType.STAYOVER);
            stubAssignReady(task);

            service.assignTask(task.getId(), request(today));

            verify(taskRepository, never()).search(any(), any(), any(), any(), any(), any(), any());
            verify(taskRepository, never())
                .findByAssignedStaffIdAndStatusAndAssignedDateGreaterThan(any(), any(), any());
        }

        @Test
        void shouldRejectAssignWhenStaffNotHousekeeping() {
            HousekeepingTask task = unassignedTask(HousekeepingTaskType.CHECKOUT);
            stubTask(task);
            stubLocation();
            stubStaff(housekeepingStaff());
            when(positionRepository.existsByIdInAndPositionType(any(), eq(PositionType.HOUSEKEEPING)))
                .thenReturn(false);

            assertThatThrownBy(() -> service.assignTask(task.getId(), request(today)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Dọn dẹp");
        }

        /** Nhân viên đa nhiệm: Loại Dọn dẹp được tìm trên CẢ vị trí chính lẫn vị trí kiêm nhiệm. */
        @Test
        void shouldCheckHousekeepingAcrossPrimaryAndExtraPositions() {
            HousekeepingTask task = unassignedTask(HousekeepingTaskType.STAYOVER);
            User multiRole = housekeepingStaff();
            multiRole.setExtraPositionIds(new LinkedHashSet<>(List.of(EXTRA_POSITION_ID)));
            stubAssignReady(task, multiRole);

            service.assignTask(task.getId(), request(today));

            verify(positionRepository).existsByIdInAndPositionType(
                Set.of(POSITION_ID, EXTRA_POSITION_ID), PositionType.HOUSEKEEPING);
            assertThat(task.getAssignedStaffId()).isEqualTo(STAFF_ID);
        }

        @Test
        void shouldRejectAssignWhenStaffInAnotherLocation() {
            HousekeepingTask task = unassignedTask(HousekeepingTaskType.CHECKOUT);
            stubTask(task);
            stubLocation();
            User elsewhere = housekeepingStaff();
            elsewhere.setLocationId(OTHER_LOCATION_ID);
            stubStaff(elsewhere);

            assertThatThrownBy(() -> service.assignTask(task.getId(), request(today)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không thuộc Location");
        }

        @Test
        void shouldRejectAssignWhenStaffTerminated() {
            HousekeepingTask task = unassignedTask(HousekeepingTaskType.CHECKOUT);
            stubTask(task);
            stubLocation();
            User terminated = housekeepingStaff();
            terminated.setStatus(UserStatus.TERMINATED);
            stubStaff(terminated);

            assertThatThrownBy(() -> service.assignTask(task.getId(), request(today)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đang làm việc");
        }

        /** BR-SCH-17: "hôm nay" theo múi giờ KHÁCH SẠN, nên phải tra Location chứ không dùng giờ máy chủ. */
        @Test
        void shouldRejectAssignForPastDateInLocationTimezone() {
            HousekeepingTask task = unassignedTask(HousekeepingTaskType.CHECKOUT);
            stubTask(task);
            stubLocation();

            assertThatThrownBy(() -> service.assignTask(task.getId(), request(today.minusDays(1))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ngày đã qua");

            verify(locationRepository).findByIdAndTenantId(LOCATION_ID, TENANT_ID);
            verifyNoInteractions(userRepository, roomStatusService);
        }

        /**
         * Q5: gán việc dọn sau check-out là phòng sang «Đang dọn» NGAY, nên gán trước cho ngày mai
         * sẽ khiến phòng hiện sai trạng thái suốt hôm nay.
         */
        @Test
        void shouldRejectFutureDateForCheckoutTask() {
            HousekeepingTask task = unassignedTask(HousekeepingTaskType.CHECKOUT);
            stubTask(task);
            stubLocation();

            assertThatThrownBy(() -> service.assignTask(task.getId(), request(today.plusDays(1))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chỉ gán được cho hôm nay")
                .hasMessageNotContaining("BR-");
            verifyNoInteractions(roomStatusService);
        }

        /** Ngược lại: dọn hằng ngày không đụng phòng nên xếp trước cho ngày mai được. */
        @Test
        void shouldAllowFutureDateForStayoverTask() {
            HousekeepingTask task = unassignedTask(HousekeepingTaskType.STAYOVER);
            stubAssignReady(task);
            when(shiftRepository.existsByStaffIdAndShiftDate(STAFF_ID, today.plusDays(1))).thenReturn(true);

            service.assignTask(task.getId(), request(today.plusDays(1)));

            assertThat(task.getAssignedDate()).isEqualTo(today.plusDays(1));
        }

        @Test
        void shouldRejectAssignWhenTaskNotUnassigned() {
            HousekeepingTask task = unassignedTask(HousekeepingTaskType.CHECKOUT);
            task.setStatus(HousekeepingTaskStatus.IN_PROGRESS);
            stubTask(task);

            assertThatThrownBy(() -> service.assignTask(task.getId(), request(today)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã có người phụ trách");
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

        /** Không còn ai dọn thì phòng quay về «Chờ dọn»; task cũ VẪN MỞ nên không sinh task mới. */
        @Test
        void shouldRevertRoomToDirtyWhenUnassigningCheckoutTask() {
            HousekeepingTask task = inProgressTask(HousekeepingTaskType.CHECKOUT);
            Room room = stubTaskAndRoom(task);

            service.unassignTask(task.getId());

            assertThat(task.getStatus()).isEqualTo(HousekeepingTaskStatus.UNASSIGNED);
            assertThat(task.getAssignedStaffId()).isNull();
            assertThat(task.getUnassignedReason()).isEqualTo(UnassignedReason.MANAGER_MANUAL);
            verify(roomStatusService).revertToDirty(room, task.getId(), "Quản lý gỡ người khỏi việc dọn");
        }

        @Test
        void shouldNotTouchRoomWhenUnassigningStayoverTask() {
            HousekeepingTask task = inProgressTask(HousekeepingTaskType.STAYOVER);
            stubTaskAndRoom(task);

            service.unassignTask(task.getId());

            assertThat(task.getStatus()).isEqualTo(HousekeepingTaskStatus.UNASSIGNED);
            verifyNoInteractions(roomStatusService);
        }
    }

    // ── Hoàn thành — BR-HK-05, BR-HK-06, BR-PERM-05 ─────────────────────────

    @Nested
    class CompleteTask {

        /** BR-HK-06: dọn sau check-out CHƯA xong — còn chờ Manager nghiệm thu. */
        @Test
        void shouldMoveCheckoutTaskToPendingInspectionOnComplete() {
            HousekeepingTask task = inProgressTask(HousekeepingTaskType.CHECKOUT);
            TestAuth.loginAs(STAFF_ID, Role.STAFF, TENANT_ID, LOCATION_ID, PositionType.HOUSEKEEPING);
            Room room = stubTaskAndRoom(task);

            service.completeTask(task.getId());

            assertThat(task.getStatus()).isEqualTo(HousekeepingTaskStatus.PENDING_INSPECTION);
            assertThat(task.getCompletedAt()).isNull();   // chưa xong thì chưa có mốc hoàn thành
            verify(roomStatusService).markPendingInspection(room, task.getId());
        }

        /** BR-HK-05: dọn hằng ngày xong là xong, và phòng vẫn «Đang sử dụng». */
        @Test
        void shouldCompleteStayoverDirectlyWithoutTouchingRoom() {
            HousekeepingTask task = inProgressTask(HousekeepingTaskType.STAYOVER);
            TestAuth.loginAs(STAFF_ID, Role.STAFF, TENANT_ID, LOCATION_ID, PositionType.HOUSEKEEPING);
            stubTaskAndRoom(task);

            service.completeTask(task.getId());

            assertThat(task.getStatus()).isEqualTo(HousekeepingTaskStatus.COMPLETED);
            assertThat(task.getCompletedAt()).isNotNull();
            verifyNoInteractions(roomStatusService);
        }

        /** BR-PERM-05: chỉ người được phân công mới bấm hoàn thành được. */
        @Test
        void shouldRejectCompleteByAnotherStaff() {
            HousekeepingTask task = inProgressTask(HousekeepingTaskType.CHECKOUT);
            TestAuth.loginAsStaff(TENANT_ID, LOCATION_ID, PositionType.HOUSEKEEPING);   // id khác
            stubTask(task);

            assertThatThrownBy(() -> service.completeTask(task.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("được phân công");
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

        /** BR-HK-06: dọn hằng ngày không đi qua bước nghiệm thu. */
        @Test
        void shouldRejectInspectingStayoverTask() {
            HousekeepingTask task = task(HousekeepingTaskType.STAYOVER,
                HousekeepingTaskStatus.IN_PROGRESS, STAFF_ID);
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

    // ── Hủy task — BR-HK-09, Q13 ─────────────────────────────

    @Nested
    class CancelTask {

        @BeforeEach
        void loginAsManager() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
        }

        /** Hủy tay luôn mang lý do MANAGER_MANUAL, và KHÔNG đụng tới phòng (BR-HK-05). */
        @Test
        void shouldCancelOpenStayoverWithManagerManualReason() {
            HousekeepingTask task = inProgressTask(HousekeepingTaskType.STAYOVER);
            stubTask(task);
            lenient().when(taskRepository.save(any(HousekeepingTask.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
            lenient().when(roomRepository.findByIdAndTenantIdAndActiveTrue(ROOM_ID, TENANT_ID))
                .thenReturn(Optional.of(room()));

            service.cancelTask(task.getId());

            assertThat(task.getStatus()).isEqualTo(HousekeepingTaskStatus.CANCELLED);
            assertThat(task.getCancelReason()).isEqualTo(TaskCancelReason.MANAGER_MANUAL);
            assertThat(task.getCancelledAt()).isNotNull();
            verifyNoInteractions(roomStatusService);
        }

        @Test
        void shouldRejectCancellingClosedTask() {
            HousekeepingTask task = inProgressTask(HousekeepingTaskType.STAYOVER);
            task.setStatus(HousekeepingTaskStatus.COMPLETED);
            stubTask(task);

            assertThatThrownBy(() -> service.cancelTask(task.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã đóng");
        }

        /**
         * Q13: hủy tay việc dọn sau check-out sẽ để phòng «Chờ dọn»/«Đang dọn» mà không còn
         * việc nào — câu lỗi phải chỉ ra lối đi đúng chứ không chỉ nói "không được".
         */
        @Test
        void shouldRejectCancellingCheckoutTask() {
            HousekeepingTask task = inProgressTask(HousekeepingTaskType.CHECKOUT);
            stubTask(task);

            assertThatThrownBy(() -> service.cancelTask(task.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Không khả dụng")
                .hasMessageNotContaining("BR-");
            verifyNoInteractions(roomStatusService);
        }

        @Test
        void shouldThrowNotFoundWhenCancellingTaskOfAnotherLocation() {
            HousekeepingTask task = inProgressTask(HousekeepingTaskType.STAYOVER);
            task.setLocationId(OTHER_LOCATION_ID);
            stubTask(task);

            assertThatThrownBy(() -> service.cancelTask(task.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── Gỡ ca tương lai — BR-HK-07, BR-SCH-17 ───────────────────────────────

    @Nested
    class ReleaseFutureTasks {

        @Test
        void shouldReleaseFutureCheckoutTasksAndRevertRooms() {
            TestAuth.loginAsDirector(TENANT_ID);
            HousekeepingTask checkout = inProgressTask(HousekeepingTaskType.CHECKOUT);
            HousekeepingTask stayover = inProgressTask(HousekeepingTaskType.STAYOVER);
            when(taskRepository.findByAssignedStaffIdAndStatusAndAssignedDateGreaterThan(
                STAFF_ID, HousekeepingTaskStatus.IN_PROGRESS, today))
                .thenReturn(List.of(checkout, stayover));
            Room room = room();
            when(roomRepository.findByIdAndTenantIdAndActiveTrue(ROOM_ID, TENANT_ID))
                .thenReturn(Optional.of(room));

            int released = service.releaseFutureTasks(STAFF_ID, UnassignedReason.TERMINATION, today);

            assertThat(released).isEqualTo(2);
            assertThat(checkout.getUnassignedReason()).isEqualTo(UnassignedReason.TERMINATION);
            // Chỉ task dọn sau check-out mới kéo phòng về «Chờ dọn»; câu lý do tự giải thích được.
            verify(roomStatusService).revertToDirty(room, checkout.getId(), "Người dọn phòng đã nghỉ việc");
            verify(roomStatusService, never()).revertToDirty(any(), eq(stayover.getId()), any());
        }

        /** BR-SCH-17: chỉ ngày LỚN HƠN hôm nay — task của chính hôm nay phải giữ nguyên. */
        @Test
        void shouldPassTodayToRepositorySoTodayTasksAreKept() {
            TestAuth.loginAsDirector(TENANT_ID);
            when(taskRepository.findByAssignedStaffIdAndStatusAndAssignedDateGreaterThan(
                any(), any(), any())).thenReturn(List.of());

            service.releaseFutureTasks(STAFF_ID, UnassignedReason.LEAVE_APPROVED, today);

            verify(taskRepository).findByAssignedStaffIdAndStatusAndAssignedDateGreaterThan(
                STAFF_ID, HousekeepingTaskStatus.IN_PROGRESS, today);
        }
    }

    // ── Phạm vi dữ liệu ─────────────────────────────────────────────────────

    @Nested
    class Scope {

        @Test
        void shouldThrowNotFoundForTaskOfAnotherTenant() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            UUID foreignTaskId = UUID.randomUUID();
            when(taskRepository.findByIdAndTenantId(foreignTaskId, TENANT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.unassignTask(foreignTaskId))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        /** Q8: ngoài Location của Manager trả 404 chứ không phải 400 — không lộ task có tồn tại. */
        @Test
        void shouldThrowNotFoundWhenManagerTouchesTaskOfAnotherLocation() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            HousekeepingTask task = inProgressTask(HousekeepingTaskType.CHECKOUT);
            task.setLocationId(OTHER_LOCATION_ID);
            stubTask(task);

            assertThatThrownBy(() -> service.unassignTask(task.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
            verifyNoInteractions(roomStatusService);
        }

        /** BR-PERM-05: nhân viên dọn không xem được task của người khác. */
        @Test
        void shouldHideTaskOfAnotherStaffFromHousekeepingStaff() {
            TestAuth.loginAsStaff(TENANT_ID, LOCATION_ID, PositionType.HOUSEKEEPING);
            HousekeepingTask task = inProgressTask(HousekeepingTaskType.CHECKOUT);   // gán cho STAFF_ID
            stubTask(task);

            assertThatThrownBy(() -> service.getTask(task.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void shouldListAssignableStaffOfManagersLocationOnly() {
            TestAuth.loginAsManager(TENANT_ID, LOCATION_ID);
            when(assignableStaffRepository.findAssignable(TENANT_ID, LOCATION_ID, today,
                Role.STAFF, UserStatus.ACTIVE, PositionType.HOUSEKEEPING))
                .thenReturn(List.of(housekeepingStaff()));

            List<AssignableStaffResponse> staff = service.getAssignableStaff(today);

            assertThat(staff).singleElement()
                .satisfies(s -> assertThat(s.getFullName()).isEqualTo("Phạm Dọn Dẹp"));
            // Location lấy từ người đang đăng nhập, không nhận từ tham số — không xem chéo được.
            verify(assignableStaffRepository).findAssignable(TENANT_ID, LOCATION_ID, today,
                Role.STAFF, UserStatus.ACTIVE, PositionType.HOUSEKEEPING);
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

    private static HousekeepingTask pendingInspectionTask() {
        return task(HousekeepingTaskType.CHECKOUT, HousekeepingTaskStatus.PENDING_INSPECTION, STAFF_ID);
    }

    /** Stub đủ để một lệnh gán chạy trót lọt; trả về phòng để test verify lời gọi RoomStatusService. */
    private Room stubAssignReady(HousekeepingTask task) {
        return stubAssignReady(task, housekeepingStaff());
    }

    private Room stubAssignReady(HousekeepingTask task, User staff) {
        stubTask(task);
        stubLocation();
        stubStaff(staff);
        lenient().when(shiftRepository.existsByStaffIdAndShiftDate(STAFF_ID, today)).thenReturn(true);
        Room room = room();
        lenient().when(roomRepository.findByIdAndTenantIdAndActiveTrue(ROOM_ID, TENANT_ID))
            .thenReturn(Optional.of(room));
        lenient().when(taskRepository.save(any(HousekeepingTask.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        return room;
    }

    private Room stubTaskAndRoom(HousekeepingTask task) {
        stubTask(task);
        Room room = room();
        when(roomRepository.findByIdAndTenantIdAndActiveTrue(ROOM_ID, TENANT_ID)).thenReturn(Optional.of(room));
        lenient().when(taskRepository.save(any(HousekeepingTask.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        return room;
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
        when(userRepository.findByIdAndTenantId(STAFF_ID, TENANT_ID)).thenReturn(Optional.of(staff));
        lenient().when(positionRepository.existsByIdInAndPositionType(any(), eq(PositionType.HOUSEKEEPING)))
            .thenReturn(true);
    }

    private static AssignTaskRequest request(LocalDate date) {
        AssignTaskRequest request = new AssignTaskRequest();
        request.setStaffId(STAFF_ID);
        request.setAssignedDate(date);
        return request;
    }

    private static User housekeepingStaff() {
        return User.builder()
            .id(STAFF_ID).tenantId(TENANT_ID).locationId(LOCATION_ID).positionId(POSITION_ID)
            .role(Role.STAFF).status(UserStatus.ACTIVE)
            .email("dondep@test.local").passwordHash("x").fullName("Phạm Dọn Dẹp").phone("0900000000")
            .build();
    }

    private static Room room() {
        return Room.builder()
            .id(ROOM_ID).tenantId(TENANT_ID).locationId(LOCATION_ID)
            .roomNumber("201").floor("2").roomTypeId(UUID.randomUUID()).capacity(2)
            .status(RoomStatus.DIRTY)
            .build();
    }

    private static HousekeepingTask unassignedTask(HousekeepingTaskType type) {
        return task(type, HousekeepingTaskStatus.UNASSIGNED, null);
    }

    private static HousekeepingTask inProgressTask(HousekeepingTaskType type) {
        return task(type, HousekeepingTaskStatus.IN_PROGRESS, STAFF_ID);
    }

    private static HousekeepingTask task(HousekeepingTaskType type, HousekeepingTaskStatus status,
                                         UUID assignedStaffId) {
        return HousekeepingTask.builder()
            .id(UUID.randomUUID())
            .tenantId(TENANT_ID)
            .locationId(LOCATION_ID)
            .roomId(ROOM_ID)
            .taskType(type)
            .status(status)
            .assignedStaffId(assignedStaffId)
            .createdSource(type == HousekeepingTaskType.CHECKOUT
                ? TaskCreatedSource.CHECKOUT_AUTO : TaskCreatedSource.MANAGER_STAYOVER)
            .build();
    }
}
