package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.CreateLeaveRequestRequest;
import com.example.SWP391_G2_SE2055_JV.dto.LeaveApprovalResponse;
import com.example.SWP391_G2_SE2055_JV.dto.LeaveApprovalResponse.ShiftImpact;
import com.example.SWP391_G2_SE2055_JV.dto.LeaveBalanceResponse;
import com.example.SWP391_G2_SE2055_JV.dto.LeaveRequestResponse;
import com.example.SWP391_G2_SE2055_JV.entity.LeaveRequest;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.Position;
import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.entity.ShiftTemplate;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.LeaveRequestStatus;
import com.example.SWP391_G2_SE2055_JV.enums.LeaveType;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.UnassignedReason;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.LeaveRequestRepository;
import com.example.SWP391_G2_SE2055_JV.repository.LocationRepository;
import com.example.SWP391_G2_SE2055_JV.repository.PositionRepository;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftRepository;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftTemplateRepository;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import com.example.SWP391_G2_SE2055_JV.utils.ShiftTimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Đơn xin nghỉ — BR-SCH-06..08, BR-SCH-16, BR-SCH-17, BR-SCH-23, BR-SCH-27..31 (BR Milestone 2, chốt
 * 19/10/2026).
 *
 * <ul>
 *   <li>Staff và Manager gửi đơn cho chính mình. Manager duyệt đơn Staff trong khách sạn mình, Giám đốc duyệt
 *       đơn Manager; không ai duyệt được đơn của chính mình, và Giám sát vận hành là Staff nên không duyệt
 *       (BR-PERM-07).</li>
 *   <li>Hai loại nghỉ: phép năm — trừ quỹ ({@link LeaveQuota}), gửi trước tối thiểu số ngày báo trước — và
 *       không lương, gửi được cả cho hôm nay. Không gửi cho ngày đã qua; một người không có hai đơn còn hiệu
 *       lực trùng ngày.</li>
 *   <li>Duyệt đơn: gỡ người khỏi các ca từ NGÀY MAI trở đi trong khoảng nghỉ, lý do LEAVE_APPROVED; ca của chính
 *       hôm nay giữ nguyên để Manager tự quyết (BR-SCH-17). Ca theo mẫu mất lễ tân duy nhất chỉ cảnh báo, vẫn
 *       duyệt được (BR-SCH-29).</li>
 * </ul>
 *
 * <p>Việc dọn phòng của người nghỉ (BR-HK-07) không phải gỡ ở đây: việc dọn chỉ giao được cho HÔM NAY
 * ({@code HousekeepingService}, Q5) còn duyệt nghỉ chỉ gỡ từ ngày mai, nên trong khoảng gỡ không có việc dọn nào.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveRequestService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Đơn còn hiệu lực — người gửi không có thêm đơn nào trùng ngày với chúng. */
    private static final Set<LeaveRequestStatus> ACTIVE =
        EnumSet.of(LeaveRequestStatus.PENDING, LeaveRequestStatus.APPROVED);

    private final LeaveRequestRepository  leaveRequestRepository;
    private final UserRepository          userRepository;
    private final PositionRepository      positionRepository;
    private final LocationRepository      locationRepository;
    private final ShiftRepository         shiftRepository;
    private final ShiftTemplateRepository shiftTemplateRepository;
    private final ReceptionCoverageRule   receptionCoverage;
    private final LeaveQuota              leaveQuota;

    // ── Người gửi ───────────────────────────────────────────────────────────

    /** Gửi đơn cho chính mình — BR-SCH-06, BR-SCH-07, BR-SCH-16, BR-SCH-27, BR-SCH-28. */
    @Transactional
    public LeaveRequestResponse createLeaveRequest(CreateLeaveRequestRequest request) {
        User requester = currentUserProfile();
        if (requester.getRole() != Role.STAFF && requester.getRole() != Role.MANAGER) {
            throw new BusinessException("Chỉ nhân viên và quản lý khách sạn gửi đơn nghỉ.");
        }
        if (requester.getLocationId() == null) {
            throw new BusinessException("Bạn chưa được gán khách sạn nên chưa gửi được đơn nghỉ.");
        }
        LocalDate from = request.getFromDate();
        LocalDate to = request.getToDate();
        LocalDate today = todayAt(requester.getLocationId(), requester.getTenantId());
        if (to.isBefore(from)) {
            throw new BusinessException("Ngày kết thúc nghỉ không được trước ngày bắt đầu.");
        }
        if (from.isBefore(today)) {
            throw new BusinessException("Không gửi đơn nghỉ cho ngày đã qua.");
        }
        if (request.getLeaveType() == LeaveType.ANNUAL) {
            assertAnnualLeaveAllowed(requester, from, to, today);
        }
        assertNoOverlap(requester.getId(), from, to);

        LeaveRequest leave = leaveRequestRepository.save(LeaveRequest.builder()
            .tenantId(requester.getTenantId())
            .locationId(requester.getLocationId())
            .requesterId(requester.getId())
            .leaveType(request.getLeaveType())
            .fromDate(from)
            .toDate(to)
            .reason(request.getReason().trim())
            .status(LeaveRequestStatus.PENDING)
            .build());
        log.info("{} gửi đơn nghỉ {} {} → {}", requester.getEmail(), leave.getLeaveType(), from, to);
        return toResponse(leave);
    }

    /** Đơn của chính người đang đăng nhập; {@code status} null = mọi trạng thái. */
    @Transactional(readOnly = true)
    public Page<LeaveRequestResponse> getMyLeaveRequests(LeaveRequestStatus status, Pageable pageable) {
        return toResponses(leaveRequestRepository.findOfRequester(SecurityUtils.getCurrentUserId(), status, pageable));
    }

    /** Quỹ phép năm của chính mình trong năm nay — BR-SCH-27, BR-SCH-28. */
    @Transactional(readOnly = true)
    public LeaveBalanceResponse getMyBalance() {
        User me = currentUserProfile();
        LocalDate today = todayAt(me.getLocationId(), me.getTenantId());
        return leaveQuota.balanceOf(me, today.getYear(), today);
    }

    /** BR-SCH-16: người gửi tự hủy khi đơn còn chờ duyệt; đã duyệt thì không hủy được. */
    @Transactional
    public LeaveRequestResponse cancelLeaveRequest(UUID id) {
        LeaveRequest leave = getOwnedRequest(id);
        if (!leave.getRequesterId().equals(SecurityUtils.getCurrentUserId())) {
            throw new ResourceNotFoundException("LeaveRequest", "id", id);
        }
        if (!leave.isPending()) {
            throw new BusinessException("Chỉ hủy được đơn đang chờ duyệt.");
        }
        leave.setStatus(LeaveRequestStatus.CANCELLED);
        log.info("Người gửi hủy đơn nghỉ {}", id);
        return toResponse(leaveRequestRepository.save(leave));
    }

    // ── Người duyệt ─────────────────────────────────────────────────────────

    /**
     * Hàng đơn của người duyệt — BR-SCH-08: Manager thấy đơn của Staff khách sạn mình, Giám đốc thấy đơn của
     * các Manager. {@code status} null = mọi trạng thái.
     */
    @Transactional(readOnly = true)
    public Page<LeaveRequestResponse> getReviewQueue(LeaveRequestStatus status, Pageable pageable) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Page<LeaveRequest> page = SecurityUtils.hasRole(Role.DIRECTOR)
            ? leaveRequestRepository.findForReview(tenantId, null, Role.MANAGER, status, pageable)
            : leaveRequestRepository.findForReview(tenantId, SecurityUtils.getCurrentLocationId(), Role.STAFF,
                status, pageable);
        return toResponses(page);
    }

    /**
     * Xem trước khi duyệt: những ca sẽ bị gỡ, ca giữ nguyên, cảnh báo mất lễ tân (BR-SCH-29) và quỹ phép năm
     * của người gửi — để người duyệt quyết định khi đã biết hệ quả.
     */
    @Transactional(readOnly = true)
    public LeaveApprovalResponse previewApproval(UUID id) {
        LeaveRequest leave = getOwnedRequest(id);
        User requester = requesterInReviewScope(leave);
        LocalDate today = todayAt(leave.getLocationId(), leave.getTenantId());
        return toApprovalResponse(leave, impactOn(leave, requester, today), balanceIfAnnual(leave, requester, today));
    }

    /**
     * Duyệt đơn — BR-SCH-07, BR-SCH-17, BR-SCH-24, BR-SCH-29, BR-SCH-31. Phép năm bị trừ quỹ từ lúc này.
     *
     * @return các ca đã gỡ / giữ, cảnh báo lễ tân (tính TRƯỚC khi gỡ) và quỹ phép sau khi duyệt
     */
    @Transactional
    public LeaveApprovalResponse approveLeaveRequest(UUID id) {
        LeaveRequest leave = getOwnedRequest(id);
        User requester = requesterInReviewScope(leave);
        assertPending(leave);
        if (requester.isTerminated()) {
            throw new BusinessException(requester.getFullName() + " đã nghỉ việc — hãy từ chối đơn này.");
        }
        LocalDate today = todayAt(leave.getLocationId(), leave.getTenantId());
        if (leave.getLeaveType() == LeaveType.ANNUAL) {
            assertQuotaCovers(leave, requester, today);
        }

        // Cảnh báo lễ tân phải tính khi người đó CÒN trong ca.
        Impact impact = impactOn(leave, requester, today);
        LocalDateTime now = nowAt(leave.getLocationId(), leave.getTenantId());
        release(impact.released(), now);

        leave.setStatus(LeaveRequestStatus.APPROVED);
        leave.setApproverId(SecurityUtils.getCurrentUserId());
        leave.setDecidedAt(now);
        leaveRequestRepository.save(leave);

        log.info("Duyệt đơn nghỉ {} của {} — gỡ {} ca, {} cảnh báo lễ tân", id, requester.getEmail(),
            impact.released().size(), impact.receptionWarnings().size());
        return toApprovalResponse(leave, impact, balanceIfAnnual(leave, requester, today));
    }

    /** Từ chối đơn — BR-SCH-08, BR-SCH-23. Lịch làm việc không đổi. */
    @Transactional
    public LeaveRequestResponse rejectLeaveRequest(UUID id) {
        LeaveRequest leave = getOwnedRequest(id);
        requesterInReviewScope(leave);
        assertPending(leave);

        leave.setStatus(LeaveRequestStatus.REJECTED);
        leave.setApproverId(SecurityUtils.getCurrentUserId());
        leave.setDecidedAt(nowAt(leave.getLocationId(), leave.getTenantId()));
        log.info("Từ chối đơn nghỉ {}", id);
        return toResponse(leaveRequestRepository.save(leave));
    }

    // ── Luật ────────────────────────────────────────────────────────────────

    /**
     * Phép năm — BR-SCH-07 bản sửa (báo trước), BR-SCH-27 (quỹ theo năm, cần Ngày bắt đầu làm việc), BR-SCH-28
     * (không vượt số ngày còn lại, tính cả đơn đang chờ duyệt).
     */
    private void assertAnnualLeaveAllowed(User requester, LocalDate from, LocalDate to, LocalDate today) {
        int notice = leaveQuota.noticeDays(requester.getTenantId());
        LocalDate earliest = today.plusDays(notice);
        if (from.isBefore(earliest)) {
            throw new BusinessException(String.format(
                "Phép năm phải gửi trước ít nhất %d ngày — sớm nhất là nghỉ từ ngày %s. Nghỉ gấp thì chọn "
                    + "Nghỉ không lương.", notice, earliest.format(DAY)));
        }
        if (from.getYear() != to.getYear()) {
            throw new BusinessException(
                "Một đơn phép năm không kéo qua hai năm vì quỹ phép tính theo từng năm — hãy tách thành hai đơn.");
        }
        if (requester.getStartWorkDate() == null) {
            throw new BusinessException("Hồ sơ của bạn chưa có Ngày bắt đầu làm việc nên chưa tính được quỹ "
                + "phép năm — nhờ quản lý cập nhật hồ sơ, hoặc chọn Nghỉ không lương.");
        }
        LeaveBalanceResponse balance = leaveQuota.balanceOf(requester, from.getYear(), today);
        int days = (int) ChronoUnit.DAYS.between(from, to) + 1;
        if (days > balance.getRemainingDays()) {
            throw new BusinessException(String.format(
                "Quỹ phép năm %d của bạn còn %d ngày (đã tính %d ngày đang chờ duyệt) — đơn này cần %d ngày. "
                    + "Rút ngắn đơn, hoặc chọn Nghỉ không lương.",
                balance.getYear(), balance.getRemainingDays(), balance.getPendingDays(), days));
        }
    }

    /** Duyệt phép năm: quỹ đã tích lũy phải còn đủ sau các đơn đã duyệt — quỹ bị trừ từ lúc này. */
    private void assertQuotaCovers(LeaveRequest leave, User requester, LocalDate today) {
        LeaveBalanceResponse balance = leaveQuota.balanceOf(requester, leave.getFromDate().getYear(), today);
        int unused = balance.getEntitledDays() - balance.getUsedDays();
        if (leave.days() > unused) {
            throw new BusinessException(String.format(
                "Quỹ phép năm %d của %s chỉ còn %d ngày chưa dùng, không đủ cho %d ngày của đơn này — hãy từ "
                    + "chối để người gửi chuyển sang nghỉ không lương.",
                balance.getYear(), requester.getFullName(), Math.max(0, unused), leave.days()));
        }
    }

    private void assertNoOverlap(UUID requesterId, LocalDate from, LocalDate to) {
        List<LeaveRequest> overlapping = leaveRequestRepository.findOverlapping(requesterId, ACTIVE, from, to);
        if (!overlapping.isEmpty()) {
            LeaveRequest other = overlapping.get(0);
            throw new BusinessException(String.format("Bạn đã có đơn nghỉ %s từ %s đến %s trùng khoảng này.",
                other.isPending() ? "đang chờ duyệt" : "đã được duyệt",
                other.getFromDate().format(DAY), other.getToDate().format(DAY)));
        }
    }

    private static void assertPending(LeaveRequest leave) {
        if (!leave.isPending()) {
            throw new BusinessException("Đơn này đã được xử lý hoặc người gửi đã hủy.");
        }
    }

    /**
     * BR-SCH-08: Manager duyệt đơn của Staff trong khách sạn mình, Giám đốc duyệt đơn của Manager. Ngoài phạm
     * vi — kể cả đơn của chính mình — trả 404 như cả codebase, để không lộ đơn có tồn tại.
     *
     * @return hồ sơ người gửi
     */
    private User requesterInReviewScope(LeaveRequest leave) {
        User requester = userRepository.findByIdAndTenantId(leave.getRequesterId(), leave.getTenantId())
            .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", "id", leave.getId()));
        boolean inScope = SecurityUtils.hasRole(Role.DIRECTOR)
            ? requester.getRole() == Role.MANAGER
            : SecurityUtils.hasRole(Role.MANAGER)
                && requester.getRole() == Role.STAFF
                && leave.getLocationId().equals(SecurityUtils.getCurrentLocationId());
        if (!inScope) {
            throw new ResourceNotFoundException("LeaveRequest", "id", leave.getId());
        }
        return requester;
    }

    // ── Hệ quả lên lịch làm việc ────────────────────────────────────────────

    /** Ca bị gỡ, ca giữ nguyên kèm lý do, cảnh báo lễ tân và tên mẫu của các ca đó. */
    private record Impact(List<Shift> released, List<Kept> kept, List<String> receptionWarnings,
                          Map<UUID, String> templateNames) {}

    private record Kept(Shift shift, String note) {}

    /**
     * Ca của người gửi trong khoảng nghỉ, chia theo BR-SCH-17: ca từ ngày mai chưa check-in thì gỡ; ca của chính
     * hôm nay và ca đã check-in thì giữ; ca đã qua là lịch sử, bỏ qua.
     */
    private Impact impactOn(LeaveRequest leave, User requester, LocalDate today) {
        List<Shift> shifts = shiftRepository.findByStaffIdAndShiftDateBetweenOrderByShiftDateAscStartTimeAsc(
            requester.getId(), leave.getFromDate(), leave.getToDate());
        List<Shift> released = new ArrayList<>();
        List<Kept> kept = new ArrayList<>();
        for (Shift shift : shifts) {
            if (shift.getShiftDate().isBefore(today)) {
                continue;
            }
            if (shift.getShiftDate().equals(today)) {
                kept.add(new Kept(shift, "Ca của hôm nay không tự gỡ — gỡ tay ở màn Xếp lịch nếu người này nghỉ luôn hôm nay."));
            } else if (shift.getCheckInAt() != null) {
                kept.add(new Kept(shift, "Đã check-in — gỡ người sẽ mất dữ liệu chấm công."));
            } else {
                released.add(shift);
            }
        }
        Map<UUID, String> templateNames = templateNamesOf(shifts);
        List<String> warnings = released.stream()
            .filter(receptionCoverage::losesOnlyReceptionist)
            .map(shift -> shiftLabel(shift, templateNames))
            .toList();
        return new Impact(released, kept, warnings, templateNames);
    }

    /**
     * Gỡ người khỏi các ca bị đơn nghỉ trùng — BR-SCH-07, BR-SCH-24. Bản ghi ca giữ nguyên (DM-03), chỉ bỏ người
     * và ghi lý do. Không đi qua luật lễ tân của {@code ShiftService.unassignStaff}: ở đây BR-SCH-29 chỉ cảnh
     * báo chứ không chặn.
     */
    private void release(List<Shift> shifts, LocalDateTime now) {
        for (Shift shift : shifts) {
            shift.setStaffId(null);
            shift.setUnassignedReason(UnassignedReason.LEAVE_APPROVED);
            shift.setUnassignedAt(now);
        }
        shiftRepository.saveAll(shifts);
    }

    private LeaveBalanceResponse balanceIfAnnual(LeaveRequest leave, User requester, LocalDate today) {
        return leave.getLeaveType() == LeaveType.ANNUAL
            ? leaveQuota.balanceOf(requester, leave.getFromDate().getYear(), today)
            : null;
    }

    /** "Ca chiều ngày 28/10/2026"; ca tự nhập giờ thì ghi khung giờ. */
    private static String shiftLabel(Shift shift, Map<UUID, String> templateNames) {
        String name = shift.getSourceTemplateId() == null ? null : templateNames.get(shift.getSourceTemplateId());
        String label = name != null ? name : "Ca " + shift.getStartTime() + "–" + shift.getEndTime();
        return label + " ngày " + shift.getShiftDate().format(DAY);
    }

    private Map<UUID, String> templateNamesOf(Collection<Shift> shifts) {
        Set<UUID> ids = shifts.stream().map(Shift::getSourceTemplateId).filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return shiftTemplateRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(ShiftTemplate::getId, ShiftTemplate::getName));
    }

    // ── Dựng response ───────────────────────────────────────────────────────

    private LeaveApprovalResponse toApprovalResponse(LeaveRequest leave, Impact impact, LeaveBalanceResponse balance) {
        return LeaveApprovalResponse.builder()
            .request(toResponse(leave))
            .releasedShifts(impact.released().stream()
                .map(shift -> shiftImpact(shift, impact.templateNames(), null))
                .toList())
            .keptShifts(impact.kept().stream()
                .map(kept -> shiftImpact(kept.shift(), impact.templateNames(), kept.note()))
                .toList())
            .receptionWarnings(impact.receptionWarnings())
            .balance(balance)
            .build();
    }

    private static ShiftImpact shiftImpact(Shift shift, Map<UUID, String> templateNames, String note) {
        String templateName = shift.getSourceTemplateId() == null ? null : templateNames.get(shift.getSourceTemplateId());
        return new ShiftImpact(shift.getId(), shift.getShiftDate(), shift.getStartTime(), shift.getEndTime(),
            templateName, note);
    }

    private LeaveRequestResponse toResponse(LeaveRequest leave) {
        return toResponses(List.of(leave)).get(0);
    }

    private Page<LeaveRequestResponse> toResponses(Page<LeaveRequest> page) {
        return new PageImpl<>(toResponses(page.getContent()), page.getPageable(), page.getTotalElements());
    }

    /** Người gửi, người duyệt, vị trí và khách sạn cho CẢ trang — mỗi loại một câu truy vấn, tránh N+1. */
    private List<LeaveRequestResponse> toResponses(List<LeaveRequest> leaves) {
        Set<UUID> userIds = leaves.stream()
            .flatMap(leave -> Stream.of(leave.getRequesterId(), leave.getApproverId()))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<UUID, User> users = userIds.isEmpty() ? Map.of()
            : userRepository.findAllById(userIds).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Set<UUID> positionIds = users.values().stream().map(User::getPositionId).filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<UUID, String> positionNames = positionIds.isEmpty() ? Map.of()
            : positionRepository.findAllById(positionIds).stream()
                .collect(Collectors.toMap(Position::getId, Position::getName));
        Map<UUID, String> locationNames = locationRepository.findNamesByIds(
            leaves.stream().map(LeaveRequest::getLocationId).collect(Collectors.toSet()));

        return leaves.stream().map(leave -> {
            User requester = users.get(leave.getRequesterId());
            String positionName = requester == null || requester.getPositionId() == null
                ? null : positionNames.get(requester.getPositionId());
            User approver = leave.getApproverId() == null ? null : users.get(leave.getApproverId());
            return LeaveRequestResponse.fromEntity(leave, requester, positionName,
                locationNames.get(leave.getLocationId()), approver);
        }).toList();
    }

    // ── Tra cứu ─────────────────────────────────────────────────────────────

    private User currentUserProfile() {
        UUID id = SecurityUtils.getCurrentUserId();
        return userRepository.findByIdAndTenantId(id, SecurityUtils.getCurrentTenantId())
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    /** Chốt chặn cách ly Tenant: không bao giờ tải đơn bằng findById trần. */
    private LeaveRequest getOwnedRequest(UUID id) {
        return leaveRequestRepository.findByIdAndTenantId(id, SecurityUtils.getCurrentTenantId())
            .orElseThrow(() -> new ResourceNotFoundException("LeaveRequest", "id", id));
    }

    /** BR-SCH-17: "hôm nay" theo múi giờ của khách sạn — cùng mốc với lịch làm việc. Không có thì giờ Hà Nội. */
    private String timezoneOf(UUID locationId, UUID tenantId) {
        if (locationId == null) {
            return null;
        }
        return locationRepository.findByIdAndTenantId(locationId, tenantId).map(Location::getTimezone).orElse(null);
    }

    private LocalDate todayAt(UUID locationId, UUID tenantId) {
        return ShiftTimeUtils.todayAt(timezoneOf(locationId, tenantId));
    }

    private LocalDateTime nowAt(UUID locationId, UUID tenantId) {
        return ShiftTimeUtils.nowAt(timezoneOf(locationId, tenantId));
    }
}
