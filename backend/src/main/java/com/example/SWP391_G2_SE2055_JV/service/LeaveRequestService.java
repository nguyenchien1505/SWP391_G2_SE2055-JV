package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.ApproveLeaveRequestRequest.ReceptionReplacement;
import com.example.SWP391_G2_SE2055_JV.dto.CreateLeaveRequestRequest;
import com.example.SWP391_G2_SE2055_JV.dto.LeaveAttachmentFile;
import com.example.SWP391_G2_SE2055_JV.dto.LeaveAttachmentInfo;
import com.example.SWP391_G2_SE2055_JV.dto.LeaveAttachmentUpload;
import com.example.SWP391_G2_SE2055_JV.dto.LeaveApprovalResponse;
import com.example.SWP391_G2_SE2055_JV.dto.LeaveApprovalResponse.Candidate;
import com.example.SWP391_G2_SE2055_JV.dto.LeaveApprovalResponse.ReceptionGap;
import com.example.SWP391_G2_SE2055_JV.dto.LeaveApprovalResponse.Replacement;
import com.example.SWP391_G2_SE2055_JV.dto.LeaveApprovalResponse.ShiftImpact;
import com.example.SWP391_G2_SE2055_JV.dto.LeaveBalanceResponse;
import com.example.SWP391_G2_SE2055_JV.dto.LeaveRequestResponse;
import com.example.SWP391_G2_SE2055_JV.entity.LeaveRequest;
import com.example.SWP391_G2_SE2055_JV.entity.LeaveRequestAttachment;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.Position;
import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.entity.ShiftTemplate;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.LeaveRequestStatus;
import com.example.SWP391_G2_SE2055_JV.enums.LeaveType;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.StaffPermission;
import com.example.SWP391_G2_SE2055_JV.enums.UnassignedReason;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.LeaveRequestAttachmentRepository;
import com.example.SWP391_G2_SE2055_JV.repository.LeaveRequestRepository;
import com.example.SWP391_G2_SE2055_JV.repository.LocationRepository;
import com.example.SWP391_G2_SE2055_JV.repository.PositionRepository;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftRepository;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftTemplateRepository;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import com.example.SWP391_G2_SE2055_JV.utils.ImageFileUtils;
import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import com.example.SWP391_G2_SE2055_JV.utils.ShiftTimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
 *   <li>Duyệt đơn: gỡ người khỏi MỌI ca chưa check-in từ HÔM NAY trở đi trong khoảng nghỉ, lý do LEAVE_APPROVED
 *       (BR-SCH-17 sửa 01/11/2026 — trước đó ca của chính hôm nay giữ lại cho Manager gỡ tay); ca đã check-in giữ
 *       để không mất dữ liệu chấm công. Ca theo mẫu sẽ mất lễ tân duy nhất thì người duyệt phải chọn lễ tân thay
 *       cho từng ca mới duyệt được (BR-SCH-29 sửa, chốt 31/10/2026 — trước đó chỉ cảnh báo).</li>
 *   <li>Từ chối đơn: bắt buộc lý do, người gửi đọc được vì sao đơn không được duyệt (chốt 31/10/2026).</li>
 *   <li>Đơn đã duyệt: người gửi không tự hủy; người duyệt hủy được phần ngày chưa tới, bắt buộc lý do, quỹ phép
 *       tự hoàn (BR-SCH-16 bản sửa 28/10/2026).</li>
 * </ul>
 *
 * <p>Việc dọn phòng của người nghỉ (BR-HK-07): việc dọn chỉ giao được cho HÔM NAY ({@code HousekeepingService},
 * Q5), nên khi duyệt chỉ có thể còn việc của hôm nay — người nghỉ mất hết ca hôm nay thì rút khỏi các nhóm dọn đó.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveRequestService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Đơn còn hiệu lực — người gửi không có thêm đơn nào trùng ngày với chúng. */
    private static final Set<LeaveRequestStatus> ACTIVE =
        EnumSet.of(LeaveRequestStatus.PENDING, LeaveRequestStatus.APPROVED);

    private static final Set<LeaveRequestStatus> APPROVED_ONLY = EnumSet.of(LeaveRequestStatus.APPROVED);

    /** Ảnh minh chứng: mỗi đơn tối đa 3 ảnh, mỗi ảnh tối đa 5 MB — khớp CHECK của V18 và giới hạn multipart. */
    static final int MAX_ATTACHMENTS = 3;
    static final int MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024;

    private final LeaveRequestRepository  leaveRequestRepository;
    private final UserRepository          userRepository;
    private final PositionRepository      positionRepository;
    private final LocationRepository      locationRepository;
    private final ShiftRepository         shiftRepository;
    private final ShiftTemplateRepository shiftTemplateRepository;
    private final ReceptionCoverageRule   receptionCoverage;
    private final LeaveQuota              leaveQuota;
    // Lễ tân thay phải qua đủ Schedule Policy như khi xếp ca tay (BR-SCH-02).
    private final SchedulePolicyValidator policyValidator;
    // BR-HK-07: người nghỉ mất ca hôm nay thì rút khỏi nhóm dọn hôm nay. Không vòng bean — HousekeepingService
    // không dùng LeaveRequestService.
    private final HousekeepingService     housekeepingService;
    private final LeaveRequestAttachmentRepository attachmentRepository;

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

    /**
     * Người gửi đính kèm ảnh minh chứng cho lý do nghỉ khi đơn còn chờ duyệt (chốt 01/11/2026, thay BR-SCH-30).
     * Mỗi đơn tối đa {@value #MAX_ATTACHMENTS} ảnh; kiểu ảnh đọc từ chính nội dung tệp — JPEG, PNG, WebP, GIF.
     */
    @Transactional
    public LeaveRequestResponse addAttachments(UUID id, List<LeaveAttachmentUpload> files) {
        LeaveRequest leave = getOwnedRequest(id);
        if (!leave.getRequesterId().equals(SecurityUtils.getCurrentUserId())) {
            throw new ResourceNotFoundException("LeaveRequest", "id", id);
        }
        if (!leave.isPending()) {
            throw new BusinessException("Chỉ đính kèm ảnh khi đơn còn chờ duyệt.");
        }
        if (files == null || files.isEmpty()) {
            throw new BusinessException("Chọn ít nhất một ảnh để đính kèm.");
        }
        long existing = attachmentRepository.countByLeaveRequestId(id);
        if (existing + files.size() > MAX_ATTACHMENTS) {
            throw new BusinessException(String.format("Mỗi đơn đính kèm tối đa %d ảnh — đơn này còn thêm được %d ảnh.",
                MAX_ATTACHMENTS, Math.max(0, MAX_ATTACHMENTS - existing)));
        }

        List<LeaveRequestAttachment> attachments = new ArrayList<>();
        for (LeaveAttachmentUpload file : files) {
            String name = ImageFileUtils.displayName(file.fileName());
            byte[] data = file.data();
            if (data == null || data.length == 0) {
                throw new BusinessException("Ảnh «" + name + "» rỗng.");
            }
            if (data.length > MAX_ATTACHMENT_BYTES) {
                throw new BusinessException("Ảnh «" + name + "» lớn hơn 5 MB.");
            }
            String type = ImageFileUtils.imageTypeOf(data);
            if (type == null) {
                throw new BusinessException("«" + name + "» không phải ảnh JPG, PNG, WebP hoặc GIF.");
            }
            attachments.add(LeaveRequestAttachment.builder()
                .tenantId(leave.getTenantId())
                .leaveRequestId(id)
                .fileName(name)
                .contentType(type)
                .sizeBytes(data.length)
                .data(data)
                .build());
        }
        attachmentRepository.saveAll(attachments);
        log.info("Đính kèm {} ảnh vào đơn nghỉ {}", attachments.size(), id);
        return toResponse(leave);
    }

    /** Một ảnh đính kèm — người gửi đơn, hoặc người duyệt theo cấp (BR-SCH-08). Ngoài phạm vi trả 404 như đơn. */
    @Transactional(readOnly = true)
    public LeaveAttachmentFile getAttachment(UUID id, UUID attachmentId) {
        LeaveRequest leave = getOwnedRequest(id);
        if (!leave.getRequesterId().equals(SecurityUtils.getCurrentUserId())) {
            requesterInReviewScope(leave);
        }
        LeaveRequestAttachment attachment = attachmentRepository
            .findByIdAndLeaveRequestIdAndTenantId(attachmentId, id, leave.getTenantId())
            .orElseThrow(() -> new ResourceNotFoundException("LeaveRequestAttachment", "id", attachmentId));
        return new LeaveAttachmentFile(attachment.getFileName(), attachment.getContentType(), attachment.getData());
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
     * Xem trước khi duyệt: những ca sẽ bị gỡ, ca giữ nguyên, ca sẽ mất lễ tân duy nhất kèm các lễ tân xếp thay được
     * (BR-SCH-29 sửa), số việc dọn hôm nay sẽ gỡ theo (BR-HK-07) và quỹ phép năm của người gửi — để người duyệt
     * quyết định khi đã biết hệ quả.
     */
    @Transactional(readOnly = true)
    public LeaveApprovalResponse previewApproval(UUID id) {
        LeaveRequest leave = getOwnedRequest(id);
        User requester = requesterInReviewScope(leave);
        LocalDate today = todayAt(leave.getLocationId(), leave.getTenantId());
        Impact impact = impactOn(leave, requester, today);
        int cleaningTasks = losesTodaysShifts(leave, impact, today)
            ? housekeepingService.countTasksOfDay(requester.getId(), today)
            : 0;
        return toApprovalResponse(leave, impact, receptionGapsOf(impact, requester), List.of(), cleaningTasks,
            balanceIfAnnual(leave, requester, today));
    }

    /**
     * Duyệt đơn — BR-SCH-07, BR-SCH-17, BR-SCH-24, BR-SCH-29 (sửa 31/10/2026), BR-SCH-31. Phép năm bị trừ quỹ từ
     * lúc này.
     *
     * @param replacements lễ tân thay cho từng ca sẽ mất lễ tân duy nhất — bắt buộc đủ, nếu không thì không duyệt
     * @return các ca đã gỡ / giữ, lễ tân đã xếp thay, số việc dọn hôm nay đã gỡ và quỹ phép sau khi duyệt
     */
    @Transactional
    public LeaveApprovalResponse approveLeaveRequest(UUID id, List<ReceptionReplacement> replacements) {
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

        // Ca mất lễ tân phải tính khi người đó CÒN trong ca.
        Impact impact = impactOn(leave, requester, today);
        Map<UUID, UUID> chosen = chosenReplacements(replacements, impact, requester);
        LocalDateTime now = nowAt(leave.getLocationId(), leave.getTenantId());
        release(impact.released(), now);
        List<Replacement> assigned = assignReplacements(impact, chosen, requester);
        // BR-HK-07 — sau khi gỡ ca, để "còn ca hôm nay không" đọc đúng lịch mới.
        int releasedTasks = coversDay(leave, today)
            ? housekeepingService.releaseTasksOfDay(requester.getId(), today, UnassignedReason.LEAVE_APPROVED)
            : 0;

        leave.setStatus(LeaveRequestStatus.APPROVED);
        leave.setApproverId(SecurityUtils.getCurrentUserId());
        leave.setDecidedAt(now);
        leaveRequestRepository.save(leave);

        log.info("Duyệt đơn nghỉ {} của {} — gỡ {} ca, xếp {} lễ tân thay, gỡ {} việc dọn", id, requester.getEmail(),
            impact.released().size(), assigned.size(), releasedTasks);
        return toApprovalResponse(leave, impact, List.of(), assigned, releasedTasks,
            balanceIfAnnual(leave, requester, today));
    }

    /**
     * Từ chối đơn — BR-SCH-08, BR-SCH-23. Bắt buộc lý do (chốt 31/10/2026): người gửi đọc được vì sao đơn không được
     * duyệt. Lịch làm việc không đổi.
     */
    @Transactional
    public LeaveRequestResponse rejectLeaveRequest(UUID id, String reason) {
        LeaveRequest leave = getOwnedRequest(id);
        requesterInReviewScope(leave);
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("Nhập lý do từ chối để người gửi biết vì sao đơn không được duyệt.");
        }
        assertPending(leave);

        leave.setStatus(LeaveRequestStatus.REJECTED);
        leave.setApproverId(SecurityUtils.getCurrentUserId());
        leave.setDecidedAt(nowAt(leave.getLocationId(), leave.getTenantId()));
        leave.setRejectReason(reason.trim());
        log.info("Từ chối đơn nghỉ {}", id);
        return toResponse(leaveRequestRepository.save(leave));
    }

    /**
     * Hủy phần ngày CHƯA TỚI của đơn đã duyệt — BR-SCH-16 bản sửa 28/10/2026. Người gửi vẫn không tự hủy được;
     * việc này của người duyệt (cùng phạm vi BR-SCH-08), bắt buộc lý do.
     *
     * <ul>
     *   <li>Chưa tới ngày nghỉ → hủy cả đơn (CANCELLED, vẫn giữ người duyệt).</li>
     *   <li>Đang nghỉ dở → rút ngắn tới hết hôm nay: hôm nay coi như đã tới, cùng mốc với BR-SCH-17.</li>
     * </ul>
     * Quỹ phép không lưu số dư mà cộng lại từ các đơn đã duyệt ({@link LeaveQuota}), nên tự hoàn đúng số ngày bị
     * hủy. Ca đã gỡ lúc duyệt KHÔNG tự gán lại — Manager xếp lại nếu cần.
     */
    @Transactional
    public LeaveRequestResponse revokeLeaveRequest(UUID id, String reason) {
        LeaveRequest leave = getOwnedRequest(id);
        requesterInReviewScope(leave);
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("Nhập lý do hủy để người gửi biết vì sao kỳ nghỉ bị cắt.");
        }
        if (leave.getStatus() != LeaveRequestStatus.APPROVED) {
            throw new BusinessException("Chỉ hủy được đơn đã duyệt — đơn đang chờ thì từ chối.");
        }
        LocalDate today = todayAt(leave.getLocationId(), leave.getTenantId());
        if (!leave.getToDate().isAfter(today)) {
            throw new BusinessException("Đơn này không còn ngày nghỉ nào chưa tới để hủy.");
        }

        int restored;
        if (leave.getFromDate().isAfter(today)) {
            restored = leave.days();
            leave.setStatus(LeaveRequestStatus.CANCELLED);
        } else {
            restored = (int) ChronoUnit.DAYS.between(today, leave.getToDate());
            leave.setOriginalToDate(leave.getToDate());
            leave.setToDate(today);
        }
        leave.setRevokedBy(SecurityUtils.getCurrentUserId());
        leave.setRevokedAt(nowAt(leave.getLocationId(), leave.getTenantId()));
        leave.setRevokeReason(reason.trim());
        log.info("Hủy {} ngày chưa tới của đơn nghỉ {}", restored, id);
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

    /**
     * Ca bị gỡ, ca giữ nguyên kèm lý do, các ca bị gỡ mà sẽ mất lễ tân duy nhất (phải xếp người thay) và tên mẫu
     * của các ca đó.
     */
    private record Impact(List<Shift> released, List<Kept> kept, List<Shift> receptionGaps,
                          Map<UUID, String> templateNames) {}

    private record Kept(Shift shift, String note) {}

    /**
     * Ca của người gửi trong khoảng nghỉ, theo BR-SCH-17 sửa 01/11/2026: ca chưa check-in từ HÔM NAY trở đi thì gỡ
     * (trước đó ca của chính hôm nay giữ lại cho Manager gỡ tay); ca đã check-in thì giữ — người đó đang / đã vào
     * làm, gỡ là mất dữ liệu chấm công; ca đã qua là lịch sử, bỏ qua.
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
            if (shift.getCheckInAt() != null) {
                kept.add(new Kept(shift, "Đã check-in — giữ nguyên để không mất dữ liệu chấm công."));
            } else {
                released.add(shift);
            }
        }
        List<Shift> receptionGaps = released.stream().filter(receptionCoverage::losesOnlyReceptionist).toList();
        return new Impact(released, kept, receptionGaps, templateNamesOf(shifts));
    }

    private static boolean coversDay(LeaveRequest leave, LocalDate day) {
        return !day.isBefore(leave.getFromDate()) && !day.isAfter(leave.getToDate());
    }

    /**
     * Duyệt đơn này có làm người gửi mất hết ca hôm nay không — khi đó việc dọn hôm nay của họ bị gỡ theo (BR-HK-07).
     * Ca hôm nay đã check-in được giữ thì họ vẫn đang làm, giữ việc.
     */
    private static boolean losesTodaysShifts(LeaveRequest leave, Impact impact, LocalDate today) {
        return coversDay(leave, today)
            && impact.kept().stream().noneMatch(kept -> kept.shift().getShiftDate().equals(today));
    }

    /**
     * Gỡ người khỏi các ca bị đơn nghỉ trùng — BR-SCH-07, BR-SCH-24. Bản ghi ca giữ nguyên (DM-03), chỉ bỏ người
     * và ghi lý do. Không đi qua luật lễ tân của {@code ShiftService.unassignStaff} ("giao thêm lễ tân trước rồi mới
     * gỡ"): ca sẽ mất lễ tân đã có người thay xếp ngay sau đó, trong cùng giao dịch ({@link #assignReplacements}).
     */
    private void release(List<Shift> shifts, LocalDateTime now) {
        for (Shift shift : shifts) {
            shift.setStaffId(null);
            shift.setUnassignedReason(UnassignedReason.LEAVE_APPROVED);
            shift.setUnassignedAt(now);
        }
        shiftRepository.saveAll(shifts);
    }

    // ── Lễ tân thay — BR-SCH-29 sửa (chốt 31/10/2026) ───────────────────────
    // Mỗi ca theo mẫu phải có lễ tân (chốt 05/10/2026, ReceptionCoverageRule). Trước đây duyệt đơn chỉ cảnh báo ca sẽ
    // mất lễ tân; giờ người duyệt chọn lễ tân thay cho từng ca đó, và lễ tân thay vào CHÍNH ca của người nghỉ.

    /**
     * Mỗi ca sẽ mất lễ tân duy nhất kèm các lễ tân xếp thay được — cùng luật với lúc duyệt
     * ({@link #whyNotReplacement}), nên ai hiện trong danh sách cũng chọn được.
     */
    private List<ReceptionGap> receptionGapsOf(Impact impact, User requester) {
        if (impact.receptionGaps().isEmpty()) {
            return List.of();
        }
        Map<UUID, List<User>> receptionistsByLocation = new HashMap<>();
        for (Shift gap : impact.receptionGaps()) {
            receptionistsByLocation.computeIfAbsent(gap.getLocationId(),
                locationId -> receptionistsAt(gap.getTenantId(), locationId));
        }
        Map<UUID, String> positionNames = positionNamesOf(
            receptionistsByLocation.values().stream().flatMap(List::stream).toList());
        return impact.receptionGaps().stream()
            .map(gap -> new ReceptionGap(shiftImpact(gap, impact.templateNames(), null),
                receptionistsByLocation.get(gap.getLocationId()).stream()
                    .filter(person -> whyNotReplacement(gap, person, requester) == null)
                    .map(person -> new Candidate(person.getId(), person.getFullName(),
                        person.getPositionId() == null ? null : positionNames.get(person.getPositionId())))
                    .toList()))
            .toList();
    }

    /**
     * Lễ tân thay theo từng ca: mỗi ca sẽ mất lễ tân phải có đúng một người, và chỉ những ca đó. Lịch đổi giữa lúc
     * xem trước và lúc duyệt (ví dụ vừa có lễ tân khác vào ca) thì báo để mở lại hộp thoại, không xếp nhầm.
     */
    private Map<UUID, UUID> chosenReplacements(List<ReceptionReplacement> replacements, Impact impact,
                                               User requester) {
        Map<UUID, UUID> chosen = new LinkedHashMap<>();
        for (ReceptionReplacement replacement : replacements == null ? List.<ReceptionReplacement>of() : replacements) {
            if (chosen.put(replacement.getShiftId(), replacement.getStaffId()) != null) {
                throw new BusinessException("Mỗi ca chỉ chọn một lễ tân thay.");
            }
        }
        Set<UUID> gapIds = impact.receptionGaps().stream().map(Shift::getId).collect(Collectors.toSet());
        if (!gapIds.containsAll(chosen.keySet())) {
            throw new BusinessException(
                "Lịch làm việc vừa thay đổi — có ca không còn cần lễ tân thay. Mở lại hộp thoại duyệt để xem lại.");
        }
        List<String> missing = impact.receptionGaps().stream()
            .filter(gap -> !chosen.containsKey(gap.getId()))
            .map(gap -> shiftLabel(gap, impact.templateNames()))
            .toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(String.format(
                "Mỗi ca phải có lễ tân: %s sẽ không còn lễ tân nào khi %s nghỉ. Chọn lễ tân thay cho %s rồi mới duyệt.",
                String.join(", ", missing), requester.getFullName(), missing.size() > 1 ? "từng ca" : "ca này"));
        }
        return chosen;
    }

    /**
     * Xếp lễ tân thay vào chính ca của người nghỉ (ca vừa gỡ ở {@link #release}). Xếp lần lượt và ghi xuống từng ca,
     * nên một người thay nhiều ca thì ca sau được kiểm tra tính cả ca trước vừa nhận (giờ/tuần, ngày liên tiếp…).
     */
    private List<Replacement> assignReplacements(Impact impact, Map<UUID, UUID> chosen, User requester) {
        List<Replacement> assigned = new ArrayList<>();
        for (Shift gap : impact.receptionGaps()) {
            String label = shiftLabel(gap, impact.templateNames());
            User person = userRepository.findByIdAndTenantId(chosen.get(gap.getId()), gap.getTenantId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy lễ tân được chọn cho " + label + "."));
            String problem = whyNotReplacement(gap, person, requester);
            if (problem != null) {
                throw new BusinessException(person.getFullName() + " không thay được ở " + label + ": " + problem);
            }
            gap.setStaffId(person.getId());
            gap.setUnassignedReason(null);
            gap.setUnassignedAt(null);
            shiftRepository.saveAndFlush(gap);
            assigned.add(new Replacement(shiftImpact(gap, impact.templateNames(), null), person.getId(),
                person.getFullName()));
        }
        return assigned;
    }

    /**
     * Vì sao {@code person} KHÔNG thay được người nghỉ ở ca {@code gap}; {@code null} = thay được. Một luật cho cả danh
     * sách gợi ý lúc xem trước lẫn lựa chọn gửi lên lúc duyệt: nhân viên đang làm ở khách sạn của ca, có quyền Lễ tân,
     * không nghỉ (đơn đã duyệt) ngày đó, và qua đủ Schedule Policy như khi xếp ca tay (BR-SCH-02).
     */
    private String whyNotReplacement(Shift gap, User person, User requester) {
        if (person.getId().equals(requester.getId())) {
            return "Đây là chính người xin nghỉ.";
        }
        if (person.getRole() != Role.STAFF || !person.isActive() || !gap.getLocationId().equals(person.getLocationId())) {
            return "Không phải nhân viên đang làm việc ở khách sạn này.";
        }
        if (!person.getPermissions().contains(StaffPermission.RECEPTION)) {
            return "Không có quyền Lễ tân.";
        }
        if (!leaveRequestRepository.findOverlapping(person.getId(), APPROVED_ONLY, gap.getShiftDate(),
                gap.getShiftDate()).isEmpty()) {
            return "Đã được duyệt nghỉ ngày " + gap.getShiftDate().format(DAY) + ".";
        }
        try {
            policyValidator.validate(gap.getTenantId(), person.getId(), gap, gap.getId());
            return null;
        } catch (BusinessException ex) {
            return ex.getMessage();
        }
    }

    /**
     * Lễ tân đang làm việc ở một khách sạn — quyền Lễ tân tick theo từng người, kể cả người kiêm. Theo tên, sắp ở DB
     * như danh sách giao việc dọn phòng ("Đỗ" đứng cạnh "Dương", không bị đẩy xuống cuối).
     */
    private List<User> receptionistsAt(UUID tenantId, UUID locationId) {
        return userRepository.findByTenantIdAndLocationIdAndRole(tenantId, locationId, Role.STAFF,
                Pageable.unpaged(Sort.by("fullName")))
            .stream()
            .filter(person -> person.isActive() && person.getPermissions().contains(StaffPermission.RECEPTION))
            .toList();
    }

    private Map<UUID, String> positionNamesOf(Collection<User> people) {
        Set<UUID> ids = people.stream().map(User::getPositionId).filter(Objects::nonNull).collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of()
            : positionRepository.findAllById(ids).stream().collect(Collectors.toMap(Position::getId, Position::getName));
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

    private LeaveApprovalResponse toApprovalResponse(LeaveRequest leave, Impact impact, List<ReceptionGap> receptionGaps,
                                                     List<Replacement> replacements, int cleaningTasks,
                                                     LeaveBalanceResponse balance) {
        return LeaveApprovalResponse.builder()
            .request(toResponse(leave))
            .releasedShifts(impact.released().stream()
                .map(shift -> shiftImpact(shift, impact.templateNames(), null))
                .toList())
            .keptShifts(impact.kept().stream()
                .map(kept -> shiftImpact(kept.shift(), impact.templateNames(), kept.note()))
                .toList())
            .receptionGaps(receptionGaps)
            .replacements(replacements)
            .releasedCleaningTasks(cleaningTasks)
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

    /**
     * Người gửi, người duyệt / hủy, vị trí, khách sạn và ảnh đính kèm cho CẢ trang — mỗi loại một câu truy vấn, tránh
     * N+1. Ảnh chỉ đọc thông tin, không đọc dữ liệu ảnh.
     */
    private List<LeaveRequestResponse> toResponses(List<LeaveRequest> leaves) {
        Set<UUID> userIds = leaves.stream()
            .flatMap(leave -> Stream.of(leave.getRequesterId(), leave.getApproverId(), leave.getRevokedBy()))
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
        Map<UUID, List<LeaveAttachmentInfo>> attachments = leaves.isEmpty() ? Map.of()
            : attachmentRepository.findInfoByLeaveRequestIds(leaves.stream().map(LeaveRequest::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(LeaveAttachmentInfo::leaveRequestId));

        return leaves.stream().map(leave -> {
            User requester = users.get(leave.getRequesterId());
            String positionName = requester == null || requester.getPositionId() == null
                ? null : positionNames.get(requester.getPositionId());
            User approver = leave.getApproverId() == null ? null : users.get(leave.getApproverId());
            User revoker = leave.getRevokedBy() == null ? null : users.get(leave.getRevokedBy());
            return LeaveRequestResponse.fromEntity(leave, requester, positionName,
                locationNames.get(leave.getLocationId()), approver, revoker,
                attachments.getOrDefault(leave.getId(), List.of()));
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
