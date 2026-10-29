package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.CreateShiftSwapRequest;
import com.example.SWP391_G2_SE2055_JV.dto.ShiftResponse;
import com.example.SWP391_G2_SE2055_JV.dto.ShiftSwapRequestResponse;
import com.example.SWP391_G2_SE2055_JV.dto.SwapCandidateResponse;
import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.entity.ShiftSwapRequest;
import com.example.SWP391_G2_SE2055_JV.entity.ShiftTemplate;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.StaffPermission;
import com.example.SWP391_G2_SE2055_JV.enums.SwapRequestStatus;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.exception.SwapExpiredException;
import com.example.SWP391_G2_SE2055_JV.exception.UnauthorizedException;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftRepository;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftSwapRequestRepository;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftTemplateRepository;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import com.example.SWP391_G2_SE2055_JV.utils.ShiftTimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Đổi ca giữa hai nhân viên — BR-SCH-08..12, 18, 19, 21, 23, 25, 26 (Milestone 2, chốt 19/10/2026).
 *
 * <p>Luồng: A gửi yêu cầu đổi ca của mình lấy một ca của B → B đồng ý hoặc từ chối → Manager duyệt →
 * hoán đổi người của 2 ca, không tạo ca mới (DM-08, BR-SCH-18). Manager không có ca nên không đổi ca
 * (BR-SCH-08).
 *
 * <p>Schedule Policy kiểm cho cả hai người ở 3 lúc: A gửi (báo sớm), B đồng ý (BR-SCH-10) và Manager
 * duyệt (lịch có thể đã đổi trong lúc chờ). Mỗi người mất đúng 1 ca và nhận 1 ca, nên dùng thẳng
 * {@link SchedulePolicyValidator#validate} với ca bị mất làm {@code excludeShiftId}.
 *
 * <p>Yêu cầu đang chờ tự chuyển «Tự hủy» ({@code EXPIRED}) khi: quá hạn B trả lời (BR-SCH-11), tới giờ
 * bắt đầu của ca sớm hơn (BR-SCH-25), một trong hai ca đã check-in, đổi người, hoặc bị Manager sửa / gỡ
 * người (chốt với nhóm). Job {@link ShiftSwapExpiryJob} quét định kỳ; mỗi thao tác cũng kiểm lại.
 *
 * <p>Mọi mốc giờ theo giờ Hà Nội, cùng quy ước với {@link ShiftTimeUtils}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftSwapService {

    /** Danh sách ca đổi được chỉ xét ca trong ngần này ngày tới, để màn chọn ca không quá dài. */
    static final int CANDIDATE_DAYS = 31;

    private static final DateTimeFormatter DAY  = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final ShiftSwapRequestRepository swapRepository;
    private final ShiftRepository            shiftRepository;
    private final ShiftTemplateRepository    shiftTemplateRepository;
    private final UserRepository             userRepository;
    private final SchedulePolicyValidator    policyValidator;
    private final SchedulePolicyService      policyService;
    // BR-SCH-26: chuyển việc dọn theo người nhận ca. Không vòng bean — HousekeepingService không dùng
    // ShiftService hay ShiftSwapService.
    private final HousekeepingService        housekeepingService;

    // ── Nhân viên A: chọn ca và gửi yêu cầu ─────────────────────────────────

    /**
     * Ca của đồng nghiệp mà người đang đăng nhập đổi được với ca {@code requesterShiftId} của mình:
     * cùng khách sạn, cùng Position và cùng bộ quyền (BR-SCH-12), chưa bắt đầu, chưa check-in
     * (BR-SCH-25), chưa nằm trong yêu cầu chờ nào (BR-SCH-19), trong {@value #CANDIDATE_DAYS} ngày tới.
     * Chưa kiểm Schedule Policy ở đây — kiểm khi gửi.
     */
    @Transactional(readOnly = true)
    public List<SwapCandidateResponse> getCandidates(UUID requesterShiftId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID me = SecurityUtils.getCurrentUserId();
        LocalDateTime now = now();

        Shift mine = requireShift(requesterShiftId, tenantId);
        if (!me.equals(mine.getStaffId())) {
            throw new BusinessException("Chỉ chọn được ca của chính bạn để đổi.");
        }
        assertSwappable(mine, now, "Ca của bạn");
        assertNotInPendingRequest(mine);
        User requester = requireUser(me, tenantId);
        Set<StaffPermission> myPermissions = permissionsOf(me);

        Map<UUID, User> colleagues = userRepository.findByPositionId(requester.getPositionId()).stream()
            .filter(u -> !u.getId().equals(me))
            .filter(u -> u.getRole() == Role.STAFF && !u.isTerminated())
            .filter(u -> tenantId.equals(u.getTenantId()) && mine.getLocationId().equals(u.getLocationId()))
            .filter(u -> myPermissions.equals(permissionsOf(u.getId())))
            .collect(Collectors.toMap(User::getId, Function.identity()));
        if (colleagues.isEmpty()) {
            return List.of();
        }

        Set<UUID> held = swapRepository.findByLocationIdAndStatusIn(mine.getLocationId(),
                SwapRequestStatus.PENDING_STATUSES).stream()
            .flatMap(r -> Stream.of(r.getRequesterShiftId(), r.getPeerShiftId()))
            .collect(Collectors.toSet());

        LocalDate today = now.toLocalDate();
        List<Shift> shifts = shiftRepository.findByTenantIdAndLocationIdAndShiftDateBetween(
                tenantId, mine.getLocationId(), today, today.plusDays(CANDIDATE_DAYS), Pageable.unpaged())
            .getContent().stream()
            .filter(s -> s.getStaffId() != null && colleagues.containsKey(s.getStaffId()))
            .filter(s -> !s.isCheckedIn() && startOf(s).isAfter(now))
            .filter(s -> !held.contains(s.getId()))
            .filter(s -> !sameHours(s, mine))
            .sorted(Comparator.comparing(Shift::getShiftDate).thenComparing(Shift::getStartTime))
            .toList();

        Map<UUID, String> templateNames = templateNames(shifts);
        return shifts.stream()
            .map(s -> SwapCandidateResponse.builder()
                .staffId(s.getStaffId())
                .fullName(colleagues.get(s.getStaffId()).getFullName())
                .shift(ShiftResponse.fromEntity(s, nameOf(templateNames, s)))
                .build())
            .toList();
    }

    @Transactional
    public ShiftSwapRequestResponse create(CreateShiftSwapRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID me = SecurityUtils.getCurrentUserId();
        LocalDateTime now = now();
        if (!SecurityUtils.hasRole(Role.STAFF)) {
            throw new BusinessException("Chỉ nhân viên mới đổi ca — quản lý khách sạn không có ca làm việc.");
        }

        Shift mine = requireShift(request.getRequesterShiftId(), tenantId);
        Shift theirs = requireShift(request.getPeerShiftId(), tenantId);
        if (!me.equals(mine.getStaffId())) {
            throw new BusinessException("Chỉ đổi được ca của chính bạn.");
        }
        if (theirs.getStaffId() == null) {
            throw new BusinessException("Ca muốn nhận chưa có người làm, không có ai để đổi cùng.");
        }
        if (me.equals(theirs.getStaffId())) {
            throw new BusinessException("Không đổi ca với chính mình.");
        }
        if (!mine.getLocationId().equals(theirs.getLocationId())) {
            throw new BusinessException("Hai ca phải ở cùng khách sạn.");
        }
        assertSwappable(mine, now, "Ca của bạn");
        assertSwappable(theirs, now, "Ca muốn nhận");

        User requester = requireUser(me, tenantId);
        User peer = requireUser(theirs.getStaffId(), tenantId);
        assertSamePositionAndPermissions(requester, peer);
        assertNotInPendingRequest(mine);
        assertNotInPendingRequest(theirs);
        checkPolicy(tenantId, requester, mine, peer, theirs);

        int timeoutHours = policyService.getOrCreate(tenantId).getSwapResponseTimeoutHours();
        ShiftSwapRequest saved;
        try {
            saved = swapRepository.saveAndFlush(ShiftSwapRequest.builder()
                .tenantId(tenantId)
                .locationId(mine.getLocationId())
                .requesterId(me)
                .requesterShiftId(mine.getId())
                .peerId(peer.getId())
                .peerShiftId(theirs.getId())
                .status(SwapRequestStatus.PENDING_PEER)
                // BR-SCH-21: hạn trả lời chốt lúc gửi; Giám đốc sửa Policy sau đó không ảnh hưởng.
                .expiresAt(now.plusHours(timeoutHours))
                .build());
        } catch (DataIntegrityViolationException ex) {
            // Hai yêu cầu cùng giữ một ca gửi gần như cùng lúc — cột sinh pending_*_shift_id chặn ở DB.
            throw new BusinessException("Một trong hai ca vừa được đưa vào một yêu cầu đổi ca khác. Tải lại rồi chọn ca khác.");
        }
        log.info("Staff {} gửi yêu cầu đổi ca {} lấy ca {} của staff {}",
            me, mine.getId(), theirs.getId(), peer.getId());
        return toResponse(saved);
    }

    /** A tự hủy khi yêu cầu còn chờ (B chưa trả lời hoặc Manager chưa duyệt). */
    @Transactional
    public ShiftSwapRequestResponse cancel(UUID id) {
        ShiftSwapRequest request = requireRequest(id);
        if (!SecurityUtils.getCurrentUserId().equals(request.getRequesterId())) {
            throw new UnauthorizedException("Chỉ người gửi mới hủy được yêu cầu đổi ca này.");
        }
        if (!request.isPending()) {
            throw new BusinessException("Yêu cầu đã được xử lý xong, không hủy được nữa.");
        }
        request.setStatus(SwapRequestStatus.CANCELLED);
        request.setDecidedAt(now());
        log.info("Yêu cầu đổi ca {} — người gửi tự hủy", id);
        return toResponse(swapRepository.save(request));
    }

    // ── Đồng nghiệp B: đồng ý hoặc từ chối ──────────────────────────────────

    /** B đồng ý: kiểm lại Schedule Policy cho CẢ HAI người (BR-SCH-10), qua thì chờ Manager duyệt. */
    @Transactional(noRollbackFor = SwapExpiredException.class)
    public ShiftSwapRequestResponse accept(UUID id) {
        ShiftSwapRequest request = requireRequest(id);
        UUID tenantId = request.getTenantId();
        assertIsPeer(request);
        assertStatus(request, SwapRequestStatus.PENDING_PEER, "Yêu cầu không còn chờ bạn trả lời.");

        LocalDateTime now = now();
        Shift mine = shiftRepository.findById(request.getRequesterShiftId()).orElse(null);
        Shift theirs = shiftRepository.findById(request.getPeerShiftId()).orElse(null);
        expireIfInvalid(request, mine, theirs, now);

        User requester = requireUser(request.getRequesterId(), tenantId);
        User peer = requireUser(request.getPeerId(), tenantId);
        checkPolicy(tenantId, requester, mine, peer, theirs);

        request.setStatus(SwapRequestStatus.PENDING_MANAGER);
        request.setPeerRespondedAt(now);
        log.info("Yêu cầu đổi ca {} — đồng nghiệp đồng ý, chờ Manager duyệt", id);
        return toResponse(swapRepository.save(request));
    }

    @Transactional
    public ShiftSwapRequestResponse decline(UUID id) {
        ShiftSwapRequest request = requireRequest(id);
        assertIsPeer(request);
        assertStatus(request, SwapRequestStatus.PENDING_PEER, "Yêu cầu không còn chờ bạn trả lời.");
        LocalDateTime now = now();
        request.setStatus(SwapRequestStatus.PEER_REJECTED);
        request.setPeerRespondedAt(now);
        request.setDecidedAt(now);
        log.info("Yêu cầu đổi ca {} — đồng nghiệp từ chối", id);
        return toResponse(swapRepository.save(request));
    }

    // ── Manager: duyệt hoặc từ chối ──────────────────────────────────────────

    /**
     * Manager duyệt: khóa 2 ca, kiểm lại mọi điều kiện và Policy, hoán đổi người của 2 ca (BR-SCH-18),
     * rồi chuyển việc dọn theo người nhận ca (BR-SCH-26) — tất cả trong một transaction.
     */
    @Transactional(noRollbackFor = SwapExpiredException.class)
    public ShiftSwapRequestResponse approve(UUID id) {
        ShiftSwapRequest request = requireRequest(id);
        UUID tenantId = request.getTenantId();
        assertManagesLocation(request);
        assertStatus(request, SwapRequestStatus.PENDING_MANAGER, "Yêu cầu không còn chờ duyệt.");

        // Khóa theo thứ tự id cố định để hai lần duyệt đan nhau không khóa chéo nhau.
        UUID firstId = request.getRequesterShiftId().compareTo(request.getPeerShiftId()) < 0
            ? request.getRequesterShiftId() : request.getPeerShiftId();
        UUID secondId = firstId.equals(request.getRequesterShiftId())
            ? request.getPeerShiftId() : request.getRequesterShiftId();
        Shift first = shiftRepository.findForUpdate(firstId, tenantId).orElse(null);
        Shift second = shiftRepository.findForUpdate(secondId, tenantId).orElse(null);
        Shift mine = firstId.equals(request.getRequesterShiftId()) ? first : second;
        Shift theirs = mine == first ? second : first;

        LocalDateTime now = now();
        expireIfInvalid(request, mine, theirs, now);

        User requester = requireUser(request.getRequesterId(), tenantId);
        User peer = requireUser(request.getPeerId(), tenantId);
        checkPolicy(tenantId, requester, mine, peer, theirs);

        mine.setStaffId(peer.getId());
        theirs.setStaffId(requester.getId());
        // Flush trước khi chuyển việc dọn: bước đó hỏi "người này còn ca trong ngày không" trên lịch MỚI.
        shiftRepository.saveAllAndFlush(List.of(mine, theirs));
        housekeepingService.transferTasksOnSwap(requester.getId(), peer.getId(), mine.getShiftDate());
        housekeepingService.transferTasksOnSwap(peer.getId(), requester.getId(), theirs.getShiftDate());

        request.setStatus(SwapRequestStatus.APPROVED);
        request.setApproverId(SecurityUtils.getCurrentUserId());
        request.setDecidedAt(now);
        log.info("Yêu cầu đổi ca {} — Manager duyệt: ca {} ↔ ca {}", id, mine.getId(), theirs.getId());
        return toResponse(swapRepository.save(request));
    }

    @Transactional
    public ShiftSwapRequestResponse reject(UUID id) {
        ShiftSwapRequest request = requireRequest(id);
        assertManagesLocation(request);
        assertStatus(request, SwapRequestStatus.PENDING_MANAGER, "Yêu cầu không còn chờ duyệt.");
        request.setStatus(SwapRequestStatus.MANAGER_REJECTED);
        request.setApproverId(SecurityUtils.getCurrentUserId());
        request.setDecidedAt(now());
        log.info("Yêu cầu đổi ca {} — Manager từ chối", id);
        return toResponse(swapRepository.save(request));
    }

    // ── Danh sách ────────────────────────────────────────────────────────────

    /** Hộp thư theo người xem. */
    public enum Box {
        /** Yêu cầu tôi đã gửi (nhân viên A). */
        SENT,
        /** Yêu cầu gửi cho tôi (nhân viên B). */
        RECEIVED,
        /** Mọi yêu cầu của khách sạn mình (Manager). */
        LOCATION
    }

    /** @param statuses rỗng hoặc null = mọi trạng thái */
    @Transactional(readOnly = true)
    public Page<ShiftSwapRequestResponse> list(Box box, Collection<SwapRequestStatus> statuses, Pageable pageable) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Collection<SwapRequestStatus> filter = statuses == null || statuses.isEmpty()
            ? EnumSet.allOf(SwapRequestStatus.class) : statuses;

        Page<ShiftSwapRequest> page = switch (box) {
            case SENT -> swapRepository.findByTenantIdAndRequesterIdAndStatusIn(
                tenantId, SecurityUtils.getCurrentUserId(), filter, pageable);
            case RECEIVED -> swapRepository.findByTenantIdAndPeerIdAndStatusIn(
                tenantId, SecurityUtils.getCurrentUserId(), filter, pageable);
            case LOCATION -> {
                if (!SecurityUtils.hasRole(Role.MANAGER)) {
                    throw new UnauthorizedException("Chỉ quản lý khách sạn xem được mọi yêu cầu đổi ca của khách sạn.");
                }
                yield swapRepository.findByTenantIdAndLocationIdAndStatusIn(
                    tenantId, SecurityUtils.getCurrentLocationId(), filter, pageable);
            }
        };
        Lookups lookups = lookups(page.getContent());
        return page.map(lookups::toResponse);
    }

    // ── Gọi từ ShiftService và job ───────────────────────────────────────────

    /**
     * Manager sửa giờ / dời ngày / gỡ người khỏi một ca: yêu cầu đang chờ có dính tới ca đó tự chuyển
     * «Tự hủy» (chốt với nhóm — phương án a). Gọi trong transaction của thao tác sửa ca.
     *
     * @return số yêu cầu vừa tự hủy
     */
    @Transactional
    public int expirePendingForShift(UUID shiftId) {
        List<ShiftSwapRequest> pending = swapRepository.findByShiftAndStatusIn(shiftId, SwapRequestStatus.PENDING_STATUSES);
        LocalDateTime now = now();
        for (ShiftSwapRequest request : pending) {
            request.setStatus(SwapRequestStatus.EXPIRED);
            request.setDecidedAt(now);
        }
        swapRepository.saveAll(pending);
        if (!pending.isEmpty()) {
            log.info("Ca {} bị sửa — tự hủy {} yêu cầu đổi ca đang chờ", shiftId, pending.size());
        }
        return pending.size();
    }

    /**
     * Ca đã từng có yêu cầu đổi ca thì không xóa được: khóa ngoại {@code fk_swap_*_shift} giữ lịch sử
     * yêu cầu, và Manager vẫn có cách khác là gỡ người (khi đó yêu cầu đang chờ tự hủy).
     */
    @Transactional(readOnly = true)
    public void assertNoSwapRequests(UUID shiftId) {
        if (swapRepository.existsByRequesterShiftIdOrPeerShiftId(shiftId, shiftId)) {
            throw new BusinessException(
                "Ca này đã có yêu cầu đổi ca nên không xóa được, để giữ lịch sử. Hãy gỡ người khỏi ca thay vì xóa.");
        }
    }

    /**
     * Job định kỳ, mọi Tenant: yêu cầu đang chờ nào không còn hợp lệ thì chuyển «Tự hủy». Chạy ngoài
     * phiên đăng nhập nên không đọc {@link SecurityUtils}.
     *
     * @return số yêu cầu vừa tự hủy
     */
    @Transactional
    public int expireDue() {
        List<ShiftSwapRequest> pending = swapRepository.findByStatusIn(SwapRequestStatus.PENDING_STATUSES);
        if (pending.isEmpty()) {
            return 0;
        }
        Set<UUID> shiftIds = pending.stream()
            .flatMap(r -> Stream.of(r.getRequesterShiftId(), r.getPeerShiftId()))
            .collect(Collectors.toSet());
        Map<UUID, Shift> shifts = shiftRepository.findAllById(shiftIds).stream()
            .collect(Collectors.toMap(Shift::getId, Function.identity()));

        LocalDateTime now = now();
        List<ShiftSwapRequest> expired = pending.stream()
            .filter(r -> invalidReason(r, shifts.get(r.getRequesterShiftId()), shifts.get(r.getPeerShiftId()), now) != null)
            .toList();
        for (ShiftSwapRequest request : expired) {
            request.setStatus(SwapRequestStatus.EXPIRED);
            request.setDecidedAt(now);
        }
        swapRepository.saveAll(expired);
        return expired.size();
    }

    // ── Kiểm tra ─────────────────────────────────────────────────────────────

    /** BR-SCH-25: chỉ đổi ca chưa tới giờ bắt đầu và chưa check-in. */
    private static void assertSwappable(Shift shift, LocalDateTime now, String label) {
        if (shift.isCheckedIn()) {
            throw new BusinessException(label + " (" + describe(shift) + ") đã check-in nên không đổi được.");
        }
        if (!startOf(shift).isAfter(now)) {
            throw new BusinessException(label + " (" + describe(shift) + ") đã tới giờ bắt đầu nên không đổi được.");
        }
    }

    /** BR-SCH-12 (sửa M2): cùng ĐÚNG MỘT Position và cùng bộ quyền nghiệp vụ đã tick. */
    private void assertSamePositionAndPermissions(User requester, User peer) {
        if (peer.getRole() != Role.STAFF || peer.isTerminated()) {
            throw new BusinessException("Chỉ đổi ca được với nhân viên đang làm việc.");
        }
        if (!Objects.equals(requester.getPositionId(), peer.getPositionId())) {
            throw new BusinessException("Chỉ đổi ca được với người cùng vị trí công việc.");
        }
        if (!permissionsOf(requester.getId()).equals(permissionsOf(peer.getId()))) {
            throw new BusinessException("Chỉ đổi ca được với người có cùng quyền nghiệp vụ (Lễ tân / Dọn dẹp), "
                + "để ca không bị thiếu người làm việc đó.");
        }
    }

    /** BR-SCH-19: mỗi ca nằm trong tối đa 1 yêu cầu đang chờ, xét cả hai phía. */
    private void assertNotInPendingRequest(Shift shift) {
        if (!swapRepository.findByShiftAndStatusIn(shift.getId(), SwapRequestStatus.PENDING_STATUSES).isEmpty()) {
            throw new BusinessException("Ca " + describe(shift) + " đang nằm trong một yêu cầu đổi ca chưa xử lý. "
                + "Muốn đổi với người khác thì hủy yêu cầu đó trước.");
        }
    }

    /**
     * BR-SCH-10: lịch SAU KHI đổi của từng người phải qua Schedule Policy. A mất ca của mình và nhận ca
     * của B; B ngược lại. Lỗi ghi rõ ai vi phạm.
     */
    private void checkPolicy(UUID tenantId, User requester, Shift requesterShift, User peer, Shift peerShift) {
        validateFor(tenantId, requester, peerShift, requesterShift.getId());
        validateFor(tenantId, peer, requesterShift, peerShift.getId());
    }

    private void validateFor(UUID tenantId, User staff, Shift received, UUID givenAwayShiftId) {
        try {
            policyValidator.validate(tenantId, staff.getId(), received, givenAwayShiftId);
        } catch (BusinessException ex) {
            throw new BusinessException("Đổi ca làm " + staff.getFullName() + " vi phạm quy định xếp ca: " + ex.getMessage());
        }
    }

    /**
     * Yêu cầu đang chờ mà không còn hợp lệ thì lưu «Tự hủy» rồi báo lý do. Phương thức gọi đánh dấu
     * {@code noRollbackFor = SwapExpiredException.class} để trạng thái mới vẫn được lưu.
     */
    private void expireIfInvalid(ShiftSwapRequest request, Shift mine, Shift theirs, LocalDateTime now) {
        String reason = invalidReason(request, mine, theirs, now);
        if (reason == null) {
            return;
        }
        request.setStatus(SwapRequestStatus.EXPIRED);
        request.setDecidedAt(now);
        swapRepository.save(request);
        throw new SwapExpiredException("Yêu cầu đổi ca đã tự hủy: " + reason + ".");
    }

    /** @return lý do yêu cầu đang chờ không còn hợp lệ; {@code null} nếu vẫn hợp lệ */
    static String invalidReason(ShiftSwapRequest request, Shift mine, Shift theirs, LocalDateTime now) {
        if (mine == null || theirs == null) {
            return "một trong hai ca không còn";
        }
        if (!request.getRequesterId().equals(mine.getStaffId()) || !request.getPeerId().equals(theirs.getStaffId())) {
            return "một trong hai ca đã đổi người làm";
        }
        if (mine.isCheckedIn() || theirs.isCheckedIn()) {
            return "một trong hai ca đã check-in";
        }
        LocalDateTime earlierStart = startOf(mine).isBefore(startOf(theirs)) ? startOf(mine) : startOf(theirs);
        if (!earlierStart.isAfter(now)) {
            return "đã tới giờ bắt đầu ca mà yêu cầu chưa được duyệt";
        }
        if (request.getStatus() == SwapRequestStatus.PENDING_PEER && !request.getExpiresAt().isAfter(now)) {
            return "đồng nghiệp không trả lời trong thời gian chờ";
        }
        return null;
    }

    private static void assertStatus(ShiftSwapRequest request, SwapRequestStatus expected, String message) {
        if (request.getStatus() != expected) {
            throw new BusinessException(message);
        }
    }

    private static void assertIsPeer(ShiftSwapRequest request) {
        if (!SecurityUtils.getCurrentUserId().equals(request.getPeerId())) {
            throw new UnauthorizedException("Chỉ người được đề nghị đổi ca mới trả lời được yêu cầu này.");
        }
    }

    /** Manager chỉ duyệt yêu cầu của khách sạn mình — BR-PERM-03. */
    private static void assertManagesLocation(ShiftSwapRequest request) {
        if (!SecurityUtils.hasRole(Role.MANAGER)
                || !request.getLocationId().equals(SecurityUtils.getCurrentLocationIdOrNull())) {
            throw new UnauthorizedException("Chỉ quản lý của khách sạn này mới duyệt được yêu cầu đổi ca.");
        }
    }

    // ── Tải dữ liệu ──────────────────────────────────────────────────────────

    private ShiftSwapRequest requireRequest(UUID id) {
        return swapRepository.findByIdAndTenantId(id, SecurityUtils.getCurrentTenantId())
            .orElseThrow(() -> new ResourceNotFoundException("ShiftSwapRequest", "id", id));
    }

    private Shift requireShift(UUID id, UUID tenantId) {
        return shiftRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Shift", "id", id));
    }

    private User requireUser(UUID id, UUID tenantId) {
        return userRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    private Set<StaffPermission> permissionsOf(UUID userId) {
        return new HashSet<>(userRepository.findPermissionsOfUser(userId));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ShiftTimeUtils.HANOI_ZONE);
    }

    private static LocalDateTime startOf(Shift shift) {
        return LocalDateTime.of(shift.getShiftDate(), shift.getStartTime());
    }

    /** Cùng ngày, cùng giờ: đổi cho nhau cũng không đổi được gì. */
    private static boolean sameHours(Shift a, Shift b) {
        return a.getShiftDate().equals(b.getShiftDate())
            && a.getStartTime().equals(b.getStartTime())
            && a.getEndTime().equals(b.getEndTime());
    }

    private static String describe(Shift shift) {
        return shift.getShiftDate().format(DAY) + " " + shift.getStartTime().format(TIME)
            + "–" + shift.getEndTime().format(TIME);
    }

    // ── Dựng response ────────────────────────────────────────────────────────

    private ShiftSwapRequestResponse toResponse(ShiftSwapRequest request) {
        return lookups(List.of(request)).toResponse(request);
    }

    /** Người, ca và tên mẫu của cả một trang yêu cầu — mỗi loại một truy vấn. */
    private Lookups lookups(List<ShiftSwapRequest> requests) {
        Set<UUID> userIds = new HashSet<>();
        Set<UUID> shiftIds = new HashSet<>();
        for (ShiftSwapRequest r : requests) {
            userIds.add(r.getRequesterId());
            userIds.add(r.getPeerId());
            if (r.getApproverId() != null) {
                userIds.add(r.getApproverId());
            }
            shiftIds.add(r.getRequesterShiftId());
            shiftIds.add(r.getPeerShiftId());
        }
        Map<UUID, User> users = userRepository.findAllById(userIds).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));
        List<Shift> shiftList = shiftRepository.findAllById(shiftIds);
        Map<UUID, Shift> shifts = shiftList.stream().collect(Collectors.toMap(Shift::getId, Function.identity()));
        return new Lookups(users, shifts, templateNames(shiftList));
    }

    private record Lookups(Map<UUID, User> users, Map<UUID, Shift> shifts, Map<UUID, String> templateNames) {

        ShiftSwapRequestResponse toResponse(ShiftSwapRequest r) {
            User approver = r.getApproverId() == null ? null : users.get(r.getApproverId());
            return ShiftSwapRequestResponse.builder()
                .id(r.getId())
                .locationId(r.getLocationId())
                .status(r.getStatus())
                .requester(party(r.getRequesterId(), r.getRequesterShiftId()))
                .peer(party(r.getPeerId(), r.getPeerShiftId()))
                .expiresAt(r.getExpiresAt())
                .peerRespondedAt(r.getPeerRespondedAt())
                .approverId(r.getApproverId())
                .approverName(approver == null ? null : approver.getFullName())
                .decidedAt(r.getDecidedAt())
                .createdAt(r.getCreatedAt())
                .build();
        }

        private ShiftSwapRequestResponse.Party party(UUID staffId, UUID shiftId) {
            User user = users.get(staffId);
            Shift shift = shifts.get(shiftId);
            return ShiftSwapRequestResponse.Party.builder()
                .staffId(staffId)
                .fullName(user == null ? null : user.getFullName())
                .shift(shift == null ? null : ShiftResponse.fromEntity(shift, nameOf(templateNames, shift)))
                .build();
        }
    }

    private Map<UUID, String> templateNames(Collection<Shift> shifts) {
        Set<UUID> ids = shifts.stream()
            .map(Shift::getSourceTemplateId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return shiftTemplateRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(ShiftTemplate::getId, ShiftTemplate::getName));
    }

    private static String nameOf(Map<UUID, String> names, Shift shift) {
        return shift.getSourceTemplateId() == null ? null : names.get(shift.getSourceTemplateId());
    }
}
