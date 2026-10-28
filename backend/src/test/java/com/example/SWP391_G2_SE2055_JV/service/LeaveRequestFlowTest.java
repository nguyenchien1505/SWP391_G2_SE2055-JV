package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.CreateLeaveRequestRequest;
import com.example.SWP391_G2_SE2055_JV.dto.LeaveApprovalResponse;
import com.example.SWP391_G2_SE2055_JV.dto.LeaveRequestResponse;
import com.example.SWP391_G2_SE2055_JV.entity.Department;
import com.example.SWP391_G2_SE2055_JV.entity.LeaveRequest;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.Position;
import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.entity.ShiftTemplate;
import com.example.SWP391_G2_SE2055_JV.entity.Tenant;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.LeaveRequestStatus;
import com.example.SWP391_G2_SE2055_JV.enums.LeaveType;
import com.example.SWP391_G2_SE2055_JV.enums.LocationStatus;
import com.example.SWP391_G2_SE2055_JV.enums.PositionType;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.StaffPermission;
import com.example.SWP391_G2_SE2055_JV.enums.TenantStatus;
import com.example.SWP391_G2_SE2055_JV.enums.UnassignedReason;
import com.example.SWP391_G2_SE2055_JV.enums.UserStatus;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.repository.LeaveRequestRepository;
import com.example.SWP391_G2_SE2055_JV.support.TestAuth;
import com.example.SWP391_G2_SE2055_JV.utils.ShiftTimeUtils;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Đơn xin nghỉ trên MySQL thật (profile "test"): các CHECK của V9, truy vấn hàng duyệt nối với hồ sơ người gửi,
 * và việc gỡ ca khi duyệt — những thứ mock không chứng minh được.
 *
 * <p>Mỗi test chạy trong một transaction và tự rollback; {@link #flushAndClear()} ép SQL xuống DB rồi đọc lại.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LeaveRequestFlowTest {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired LeaveRequestService    leaveRequestService;
    @Autowired LeaveRequestRepository leaveRequestRepository;
    @Autowired LeaveQuota             leaveQuota;
    @Autowired EntityManager          em;

    private UUID tenantId;
    private UUID hanoi;
    private User director;
    private User manager;
    private User receptionist;
    private User cleaner;
    private LocalDate today;
    private LocalDate tomorrow;
    private Shift cleanerToday;
    private Shift cleanerTomorrow;
    private Shift receptionistTomorrow;

    /** "Ca chiều" ngày mai có lễ tân Lan và người dọn Hoa; Hoa có thêm ca chiều hôm nay. */
    @BeforeEach
    void setUp() {
        tenantId = persist(Tenant.builder().name("Sao Mai Hotels").contactEmail(uniqueEmail())
            .contactPhone("0900000000").status(TenantStatus.ACTIVE).build()).getId();
        hanoi = persist(Location.builder().tenantId(tenantId).name("Sao Mai Hà Nội")
            .address("Địa chỉ test").phone("0900000000").status(LocationStatus.OPERATIONAL).build()).getId();
        today = ShiftTimeUtils.todayAt("Asia/Ho_Chi_Minh");
        tomorrow = today.plusDays(1);

        UUID department = persist(Department.builder().tenantId(tenantId).name("Vận hành").build()).getId();
        UUID position = persist(Position.builder().tenantId(tenantId).departmentId(department)
            .name("Nhân viên").positionType(PositionType.OTHER).build()).getId();
        UUID afternoon = persist(ShiftTemplate.builder().tenantId(tenantId).name("Ca chiều")
            .startTime(LocalTime.of(14, 0)).endTime(LocalTime.of(22, 0)).build()).getId();

        director = persist(person(Role.DIRECTOR, null, null, "Ngô Giám Đốc"));
        manager = persist(person(Role.MANAGER, hanoi, null, "Trần Quản Lý"));
        receptionist = persist(person(Role.STAFF, hanoi, position, "Lê Thị Lan", StaffPermission.RECEPTION));
        cleaner = persist(person(Role.STAFF, hanoi, position, "Nguyễn Thị Hoa", StaffPermission.HOUSEKEEPING));

        cleanerToday = persist(shift(cleaner, today, afternoon));
        cleanerTomorrow = persist(shift(cleaner, tomorrow, afternoon));
        receptionistTomorrow = persist(shift(receptionist, tomorrow, afternoon));
        flushAndClear();
    }

    @AfterEach
    void logout() {
        TestAuth.logout();
    }

    /** BR-SCH-17: duyệt đơn từ hôm nay → gỡ ca ngày mai (LEAVE_APPROVED), ca hôm nay giữ nguyên. */
    @Test
    void shouldReleaseTomorrowsShiftAndKeepTodaysOnApproval() {
        loginAs(cleaner);
        LeaveRequestResponse sent = leaveRequestService.createLeaveRequest(unpaid(today, tomorrow));
        loginAs(manager);

        LeaveApprovalResponse result = leaveRequestService.approveLeaveRequest(sent.getId());
        flushAndClear();

        assertThat(em.find(Shift.class, cleanerToday.getId()).getStaffId()).isEqualTo(cleaner.getId());
        Shift released = em.find(Shift.class, cleanerTomorrow.getId());
        assertThat(released.getStaffId()).isNull();
        assertThat(released.getUnassignedReason()).isEqualTo(UnassignedReason.LEAVE_APPROVED);
        LeaveRequest stored = em.find(LeaveRequest.class, sent.getId());
        assertThat(stored.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
        assertThat(stored.getApproverId()).isEqualTo(manager.getId());
        assertThat(stored.getDecidedAt()).isNotNull();
        assertThat(result.getReleasedShifts()).hasSize(1);
        assertThat(result.getKeptShifts()).hasSize(1);
        // Hoa không phải lễ tân: ca chiều mai vẫn còn lễ tân Lan.
        assertThat(result.getReceptionWarnings()).isEmpty();
    }

    /** BR-SCH-29: lễ tân duy nhất của ca nghỉ → xem trước và duyệt đều nêu tên ca, nhưng vẫn duyệt được. */
    @Test
    void shouldWarnButApproveWhenOnlyReceptionistGoesOnLeave() {
        loginAs(receptionist);
        LeaveRequestResponse sent = leaveRequestService.createLeaveRequest(unpaid(tomorrow, tomorrow));
        loginAs(manager);
        String expected = "Ca chiều ngày " + tomorrow.format(DAY);

        assertThat(leaveRequestService.previewApproval(sent.getId()).getReceptionWarnings()).containsExactly(expected);
        assertThat(leaveRequestService.approveLeaveRequest(sent.getId()).getReceptionWarnings())
            .containsExactly(expected);
        flushAndClear();

        assertThat(em.find(Shift.class, receptionistTomorrow.getId()).getStaffId()).isNull();
        assertThat(em.find(Shift.class, cleanerTomorrow.getId()).getStaffId()).isEqualTo(cleaner.getId());
    }

    /** BR-SCH-08: Manager thấy đơn của Staff khách sạn mình (không thấy đơn của chính mình); Giám đốc thấy đơn Manager. */
    @Test
    void shouldScopeReviewQueueByLevel() {
        loginAs(cleaner);
        UUID staffRequest = leaveRequestService.createLeaveRequest(unpaid(tomorrow, tomorrow)).getId();
        loginAs(manager);
        UUID managerRequest = leaveRequestService.createLeaveRequest(unpaid(tomorrow, tomorrow)).getId();
        flushAndClear();

        assertThat(leaveRequestService.getReviewQueue(LeaveRequestStatus.PENDING, PageRequest.of(0, 20)).getContent())
            .extracting(LeaveRequestResponse::getId)
            .containsExactly(staffRequest);

        loginAs(director);
        assertThat(leaveRequestService.getReviewQueue(null, PageRequest.of(0, 20)).getContent())
            .singleElement()
            .satisfies(leave -> {
                assertThat(leave.getId()).isEqualTo(managerRequest);
                assertThat(leave.getRequesterName()).isEqualTo("Trần Quản Lý");
                assertThat(leave.getLocationName()).isEqualTo("Sao Mai Hà Nội");
            });
    }

    /** Một người không có hai đơn còn hiệu lực trùng ngày. */
    @Test
    void shouldRejectOverlappingRequest() {
        loginAs(cleaner);
        leaveRequestService.createLeaveRequest(unpaid(tomorrow, tomorrow));
        flushAndClear();

        assertThatThrownBy(() -> leaveRequestService.createLeaveRequest(unpaid(today, tomorrow.plusDays(1))))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("trùng khoảng này");
    }

    /** Quỹ phép năm đọc đúng đơn đã duyệt từ DB — năm cố định nên không phụ thuộc hôm chạy test. */
    @Test
    void shouldCountApprovedAnnualDaysFromDatabase() {
        persist(LeaveRequest.builder().tenantId(tenantId).locationId(hanoi).requesterId(cleaner.getId())
            .leaveType(LeaveType.ANNUAL).fromDate(LocalDate.of(2025, 6, 2)).toDate(LocalDate.of(2025, 6, 3))
            .reason("Nghỉ phép").status(LeaveRequestStatus.APPROVED)
            .approverId(manager.getId()).decidedAt(LocalDate.of(2025, 5, 20).atStartOfDay()).build());
        flushAndClear();

        User stored = em.find(User.class, cleaner.getId());
        assertThat(leaveQuota.balanceOf(stored, 2025, LocalDate.of(2025, 12, 31)))
            .satisfies(balance -> {
                assertThat(balance.getEntitledDays()).isEqualTo(12);
                assertThat(balance.getUsedDays()).isEqualTo(2);
                assertThat(balance.getRemainingDays()).isEqualTo(10);
            });
    }

    /** V9: đơn đã duyệt phải có người duyệt — DB chặn dù có đường ghi nào bỏ sót ở service. */
    @Test
    void shouldEnforceDecisionConsistencyAtDatabaseLevel() {
        LeaveRequest broken = LeaveRequest.builder().tenantId(tenantId).locationId(hanoi)
            .requesterId(cleaner.getId()).leaveType(LeaveType.UNPAID).fromDate(tomorrow).toDate(tomorrow)
            .reason("Ốm").status(LeaveRequestStatus.APPROVED).build();

        assertThatThrownBy(() -> leaveRequestRepository.saveAndFlush(broken))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("ck_leave_requests_decision");
    }

    /**
     * BR-SCH-16 bản sửa 28/10/2026: hủy cả đơn chưa tới ngày nghỉ — V10 cho phép đơn CANCELLED giữ người duyệt
     * khi có người hủy, và quỹ phép tự hoàn vì được cộng lại từ đơn đã duyệt.
     */
    @Test
    void shouldRevokeWholeApprovedLeaveAndRestoreQuota() {
        LocalDate day = today.plusDays(5);
        LeaveRequest leave = persist(approvedAnnual(day, day));
        flushAndClear();
        User stored = em.find(User.class, cleaner.getId());
        assertThat(leaveQuota.balanceOf(stored, day.getYear(), today).getUsedDays()).isEqualTo(1);

        loginAs(manager);
        leaveRequestService.revokeLeaveRequest(leave.getId(), "Khách đoàn đến sớm, cần người");
        flushAndClear();

        LeaveRequest reloaded = em.find(LeaveRequest.class, leave.getId());
        assertThat(reloaded.getStatus()).isEqualTo(LeaveRequestStatus.CANCELLED);
        assertThat(reloaded.getApproverId()).isEqualTo(manager.getId());
        assertThat(reloaded.getRevokedBy()).isEqualTo(manager.getId());
        assertThat(reloaded.getRevokeReason()).isEqualTo("Khách đoàn đến sớm, cần người");
        assertThat(leaveQuota.balanceOf(stored, day.getYear(), today).getUsedDays()).isZero();
    }

    /** Đang nghỉ dở: rút ngắn tới hôm nay — đơn vẫn APPROVED, có người hủy và ngày kết thúc cũ. */
    @Test
    void shouldShortenOngoingLeaveInDatabase() {
        LeaveRequest leave = persist(LeaveRequest.builder().tenantId(tenantId).locationId(hanoi)
            .requesterId(cleaner.getId()).leaveType(LeaveType.UNPAID).fromDate(today.minusDays(1))
            .toDate(today.plusDays(2)).reason("Ốm").status(LeaveRequestStatus.APPROVED)
            .approverId(manager.getId()).decidedAt(today.minusDays(3).atTime(9, 0)).build());
        flushAndClear();

        loginAs(manager);
        leaveRequestService.revokeLeaveRequest(leave.getId(), "Đã khỏe, xin đi làm lại");
        flushAndClear();

        LeaveRequest reloaded = em.find(LeaveRequest.class, leave.getId());
        assertThat(reloaded.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
        assertThat(reloaded.getToDate()).isEqualTo(today);
        assertThat(reloaded.getOriginalToDate()).isEqualTo(today.plusDays(2));
    }

    /** V10: có người hủy thì phải có lý do — DB chặn dù có đường ghi nào bỏ sót ở service. */
    @Test
    void shouldRequireRevokeReasonAtDatabaseLevel() {
        LeaveRequest broken = approvedAnnual(today.plusDays(5), today.plusDays(5));
        broken.setStatus(LeaveRequestStatus.CANCELLED);
        broken.setRevokedBy(manager.getId());
        broken.setRevokedAt(today.atTime(10, 0));

        assertThatThrownBy(() -> leaveRequestRepository.saveAndFlush(broken))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("ck_leave_requests_revoke");
    }

    // ── Tiện ích ────────────────────────────────────────────────────────────

    /** Phép năm của người dọn Hoa, Manager đã duyệt từ 3 ngày trước. */
    private LeaveRequest approvedAnnual(LocalDate from, LocalDate to) {
        return LeaveRequest.builder().tenantId(tenantId).locationId(hanoi).requesterId(cleaner.getId())
            .leaveType(LeaveType.ANNUAL).fromDate(from).toDate(to).reason("Nghỉ phép")
            .status(LeaveRequestStatus.APPROVED).approverId(manager.getId())
            .decidedAt(today.minusDays(3).atTime(9, 0)).build();
    }

    private void loginAs(User person) {
        PositionType type = person.getRole() == Role.STAFF ? PositionType.OTHER : null;
        TestAuth.loginAs(person.getId(), person.getRole(), tenantId, person.getLocationId(), type);
    }

    private static CreateLeaveRequestRequest unpaid(LocalDate from, LocalDate to) {
        CreateLeaveRequestRequest request = new CreateLeaveRequestRequest();
        request.setLeaveType(LeaveType.UNPAID);
        request.setFromDate(from);
        request.setToDate(to);
        request.setReason("Việc gia đình");
        return request;
    }

    private User person(Role role, UUID locationId, UUID positionId, String fullName, StaffPermission... permissions) {
        Set<StaffPermission> granted = EnumSet.noneOf(StaffPermission.class);
        granted.addAll(java.util.List.of(permissions));
        return User.builder().tenantId(tenantId).locationId(locationId).positionId(positionId).role(role)
            .permissions(granted).status(UserStatus.ACTIVE).email(uniqueEmail()).passwordHash("x")
            .fullName(fullName).phone("0900000000").startWorkDate(LocalDate.of(2024, 1, 1))
            .build();
    }

    private Shift shift(User staff, LocalDate date, UUID templateId) {
        return Shift.builder().tenantId(tenantId).locationId(hanoi).staffId(staff.getId()).shiftDate(date)
            .startTime(LocalTime.of(14, 0)).endTime(LocalTime.of(22, 0))
            .durationHours(new BigDecimal("8.00")).sourceTemplateId(templateId).build();
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }

    private static String uniqueEmail() {
        return UUID.randomUUID() + "@test.local";
    }
}
