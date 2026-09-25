package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.CreateUserRequest;
import com.example.SWP391_G2_SE2055_JV.dto.TempPasswordResponse;
import com.example.SWP391_G2_SE2055_JV.dto.TerminateUserRequest;
import com.example.SWP391_G2_SE2055_JV.dto.TerminationResponse;
import com.example.SWP391_G2_SE2055_JV.dto.UpdateUserRequest;
import com.example.SWP391_G2_SE2055_JV.dto.UserResponse;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.Position;
import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.entity.Subscription;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.LocationStatus;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.UnassignedReason;
import com.example.SWP391_G2_SE2055_JV.enums.UserStatus;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.LocationRepository;
import com.example.SWP391_G2_SE2055_JV.repository.PositionRepository;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftRepository;
import com.example.SWP391_G2_SE2055_JV.repository.SubscriptionRepository;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import com.example.SWP391_G2_SE2055_JV.utils.ShiftTimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Hồ sơ nhân sự và tài khoản đăng nhập — BR-USER-01..08.
 *
 * <p>Milestone 1 chỉ quản lý hồ sơ CƠ BẢN: không có Hợp đồng lao động và Lương
 * (BR-USER-02, BR-OUT-01).
 *
 * <p>Mật khẩu tạm KHÔNG gửi qua email: Milestone 1 không tích hợp email/SMS
 * (BR-USER-03, BR-OUT-01), nên nó được trả về đúng một lần trong response để màn
 * hình hiển thị cho Manager tự thông báo thủ công.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final int TEMP_PASSWORD_LENGTH = 10;

    private final UserRepository     userRepository;
    private final PositionRepository positionRepository;
    private final LocationRepository locationRepository;
    private final ShiftRepository    shiftRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final HousekeepingService housekeepingService;
    private final ApplicationEventPublisher eventPublisher;
    private final PasswordEncoder    passwordEncoder;

    /**
     * @param role lọc theo vai trò (ví dụ màn hình quản lý Manager chỉ cần MANAGER);
     *             {@code null} = mọi vai trò.
     */
    @Transactional(readOnly = true)
    public Page<UserResponse> getUsers(Role role, Pageable pageable) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        // BR-PERM-03: Manager chỉ thấy nhân sự trong Location của mình.
        if (SecurityUtils.hasRole(Role.MANAGER)) {
            UUID locationId = SecurityUtils.getCurrentLocationId();
            Page<User> page = role == null
                ? userRepository.findByTenantIdAndLocationId(tenantId, locationId, pageable)
                : userRepository.findByTenantIdAndLocationIdAndRole(tenantId, locationId, role, pageable);
            return page.map(UserResponse::fromEntity);
        }
        Page<User> page = role == null
            ? userRepository.findByTenantId(tenantId, pageable)
            : userRepository.findByTenantIdAndRole(tenantId, role, pageable);
        return page.map(UserResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        return UserResponse.fromEntity(getOwnedUser(id));
    }

    /**
     * BR-USER-03: tạo hồ sơ và sinh tài khoản đăng nhập trong MỘT bước, kèm mật khẩu
     * tạm bắt buộc đổi ở lần đăng nhập đầu tiên (BR-USER-07).
     */
    @Transactional
    public TempPasswordResponse createUser(CreateUserRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        assertCanCreateRole(request.getRole());
        Location location = validateProfile(request, tenantId);
        Set<UUID> extraPositions = resolveExtraPositions(
            request.getExtraPositionIds(), request.getPositionId(), Set.of(), tenantId);

        // BR-USER-06: email của người đã nghỉ việc VẪN chiếm chỗ, không dùng lại được.
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email đã tồn tại trong hệ thống: " + request.getEmail());
        }
        if (request.getRole() == Role.STAFF) {
            assertStaffQuotaAvailable(tenantId);
        }

        String tempPassword = generateTempPassword();

        User user = User.builder()
            .tenantId(tenantId)
            .role(request.getRole())
            .email(request.getEmail())
            .passwordHash(passwordEncoder.encode(tempPassword))
            .mustChangePassword(true)
            .status(UserStatus.ACTIVE)
            .fullName(request.getFullName())
            .phone(request.getPhone())
            .locationId(request.getLocationId())
            .positionId(request.getPositionId())
            .extraPositionIds(extraPositions)
            .startWorkDate(request.getStartWorkDate())
            .dateOfBirth(request.getDateOfBirth())
            .gender(request.getGender())
            .address(request.getAddress())
            .avatarUrl(request.getAvatarUrl())
            .build();

        User saved = userRepository.save(user);
        // Email là kênh bổ sung (app.mail.enabled) — mật khẩu tạm vẫn trả về cho Manager (BR-USER-03).
        eventPublisher.publishEvent(new AccountCredentialsIssuedEvent(
            saved.getEmail(), saved.getFullName(), tempPassword, false));
        log.info("Tạo tài khoản {} role={} tenant={}", saved.getEmail(), saved.getRole(), tenantId);

        // BR-ORG-02, DM-13: Location có Manager thì mới chính thức vận hành. Manager dự bị
        // (không có Location) chưa làm thay đổi khách sạn nào.
        if (saved.getRole() == Role.MANAGER && location != null) {
            location.setStatus(LocationStatus.OPERATIONAL);
            locationRepository.save(location);
        }

        return new TempPasswordResponse(UserResponse.fromEntity(saved), tempPassword);
    }

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        User user = getOwnedUser(id);
        assertCanModify(user);

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            throw new BusinessException(
                "Không đổi được email: email là username unique toàn hệ thống.");
        }
        if (user.isTerminated()) {
            throw new BusinessException("Không sửa được hồ sơ của nhân viên đã nghỉ việc.");
        }

        // Chỉ kiểm khi ĐỔI ngày: người đã đi làm thì ngày cũ nằm trong quá khứ, sửa hồ sơ
        // khác mà gửi lại nguyên ngày đó vẫn phải lưu được.
        if (request.getStartWorkDate() != null
                && !request.getStartWorkDate().equals(user.getStartWorkDate())) {
            assertStartWorkDateAfterToday(request.getStartWorkDate());
        }

        if (request.getFullName() != null)      user.setFullName(request.getFullName());
        if (request.getPhone() != null)         user.setPhone(request.getPhone());
        if (request.getStartWorkDate() != null) user.setStartWorkDate(request.getStartWorkDate());
        if (request.getDateOfBirth() != null)   user.setDateOfBirth(request.getDateOfBirth());
        if (request.getGender() != null)        user.setGender(request.getGender());
        if (request.getAddress() != null)       user.setAddress(request.getAddress());
        if (request.getAvatarUrl() != null)     user.setAvatarUrl(request.getAvatarUrl());

        // Chỉ kiểm khi ĐỔI Position: giữ nguyên một Position đã bị ẩn sau này vẫn hợp lệ.
        if (request.getPositionId() != null && !request.getPositionId().equals(user.getPositionId())) {
            if (!user.isStaff()) {
                throw new BusinessException("Chỉ STAFF mới có Position.");
            }
            assertPositionSelectable(request.getPositionId(), user.getTenantId());
            user.setPositionId(request.getPositionId());
        }

        if (request.getExtraPositionIds() != null) {
            if (!user.isStaff() && !request.getExtraPositionIds().isEmpty()) {
                throw new BusinessException("Chỉ STAFF mới kiêm nhiệm được nhiều vị trí.");
            }
            Set<UUID> extras = resolveExtraPositions(request.getExtraPositionIds(), user.getPositionId(),
                user.getExtraPositionIds(), user.getTenantId());
            user.getExtraPositionIds().retainAll(extras);
            user.getExtraPositionIds().addAll(extras);
        }
        // Đổi vị trí chính sang một vị trí đang kiêm nhiệm thì nó thôi là kiêm nhiệm.
        user.getExtraPositionIds().remove(user.getPositionId());

        if (request.getEnabled() != null) {
            user.setStatus(request.getEnabled() ? UserStatus.ACTIVE : UserStatus.INACTIVE);
        }

        if (request.getLocationId() != null && !request.getLocationId().equals(user.getLocationId())) {
            if (user.getRole() != Role.MANAGER || user.getLocationId() != null) {
                throw new BusinessException(
                    "Chỉ gán khách sạn được cho Manager dự bị; đổi khách sạn của người đang có "
                    + "khách sạn phải qua luồng điều chuyển.");
            }
            assignManagerToLocation(user, getOwnedLocationWithoutManager(request.getLocationId()));
        }

        log.info("Cập nhật hồ sơ {}", user.getEmail());
        return UserResponse.fromEntity(userRepository.save(user));
    }

    /**
     * Cho nghỉ việc — BR-USER-04. Manager thao tác trực tiếp, KHÔNG cần Giám đốc duyệt.
     *
     * <p>Xóa mềm: tài khoản chuyển TERMINATED và không đăng nhập được, dữ liệu lịch sử
     * giữ nguyên, ca TƯƠNG LAI tự động gỡ thành "chưa phân công" (BR-SCH-17, BR-SCH-24),
     * task dọn đã gán cho các ngày tương lai cũng gỡ theo (BR-HK-07).
     *
     * <p>Manager đang phụ trách khách sạn thì phải bàn giao ngay trong cùng giao dịch — cho một
     * Manager dự bị nhận, hoặc tạo Manager mới (xem {@link TerminateUserRequest}). Nhờ vậy khách
     * sạn không lúc nào "mồ côi", cùng tinh thần BR-TRF-03 của luồng điều chuyển.
     */
    @Transactional
    public TerminationResponse terminateUser(UUID id, TerminateUserRequest request) {
        User user = getOwnedUser(id);
        assertCanModify(user);

        if (user.isTerminated()) {
            throw new BusinessException("Nhân viên này đã ở trạng thái nghỉ việc.");
        }
        if (user.getRole() == Role.DIRECTOR) {
            throw new BusinessException("Không cho nghỉ việc tài khoản Giám đốc qua luồng này.");
        }

        UUID replacementId = request == null ? null : request.getReplacementManagerId();
        CreateUserRequest newManager = request == null ? null : request.getNewManager();
        boolean handsOverLocation = user.getRole() == Role.MANAGER && user.getLocationId() != null;

        if (handsOverLocation && (replacementId == null) == (newManager == null)) {
            throw new BusinessException(
                "Quản lý này đang phụ trách khách sạn: chọn MỘT Manager dự bị hoặc tạo MỘT tài "
                + "khoản Manager mới để nhận bàn giao.");
        }
        if (!handsOverLocation && (replacementId != null || newManager != null)) {
            throw new BusinessException("Tài khoản này không phụ trách khách sạn nào nên không cần bàn giao.");
        }

        // Kiểm tra người nhận TRƯỚC khi đổi gì, để lỗi chọn sai không đi kèm thay đổi dở dang.
        User reserve = replacementId == null ? null : getReserveManager(replacementId, user.getId());

        terminate(user);

        if (!handsOverLocation) {
            return new TerminationResponse(UserResponse.fromEntity(user), null, null);
        }

        // DM-13: khách sạn chuyển ngay sang người mới nên vẫn "Đang vận hành" — không có
        // khoảng trống "Chưa vận hành" như trước.
        if (reserve != null) {
            Location location = locationRepository.findByIdAndTenantId(user.getLocationId(), user.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Location", "id", user.getLocationId()));
            assignManagerToLocation(reserve, location);
            log.info("Bàn giao khách sạn {} từ {} cho Manager dự bị {}",
                location.getId(), user.getEmail(), reserve.getEmail());
            return new TerminationResponse(
                UserResponse.fromEntity(user), UserResponse.fromEntity(reserve), null);
        }

        newManager.setRole(Role.MANAGER);
        newManager.setLocationId(user.getLocationId());
        newManager.setPositionId(null);
        newManager.setExtraPositionIds(null);
        // Người cũ đã TERMINATED ở trên nên chốt "1 Location 1 Manager" trong createUser cho qua.
        TempPasswordResponse created = createUser(newManager);
        log.info("Bàn giao khách sạn {} từ {} cho Manager mới {}",
            user.getLocationId(), user.getEmail(), created.getUser().getEmail());
        return new TerminationResponse(
            UserResponse.fromEntity(user), created.getUser(), created.getTempPassword());
    }

    /**
     * Xóa VĨNH VIỄN tài khoản chưa để lại dữ liệu nào — ví dụ tạo nhầm, hoặc nghỉ trước khi bắt
     * đầu làm. Khác cho nghỉ việc (xóa mềm, BR-USER-04): dòng dữ liệu biến mất hẳn và email được
     * giải phóng.
     * <ul>
     *   <li>STAFF: Manager xóa thẳng, ở trạng thái nào cũng được.</li>
     *   <li>MANAGER: Giám đốc chỉ xóa được người ĐÃ NGHỈ VIỆC — Manager đang phụ trách khách sạn
     *       phải qua luồng cho nghỉ việc có bàn giao trước, để khách sạn không "mồ côi".</li>
     * </ul>
     *
     * <p>"Chưa tương tác với hệ thống" = không bản ghi nào tham chiếu tới tài khoản này. Mọi
     * cột trỏ tới {@code users} (created_by / updated_by, ca làm, task dọn, biên bản kiểm tra,
     * đơn nghỉ, điều chuyển…) đều có khóa ngoại, nên để DB tự chặn thay vì liệt kê bảng ở đây —
     * bảng mới thêm sau này cũng tự được tính.
     */
    @Transactional
    public void deleteUserPermanently(UUID id) {
        User user = getOwnedUser(id);
        assertCanModify(user);

        if (user.getRole() != Role.STAFF && user.getRole() != Role.MANAGER) {
            throw new BusinessException("Chỉ xóa vĩnh viễn được tài khoản Staff hoặc Manager.");
        }
        if (user.getRole() == Role.MANAGER && !user.isTerminated()) {
            throw new BusinessException("Chỉ xóa vĩnh viễn được tài khoản Manager đã nghỉ việc.");
        }

        try {
            userRepository.delete(user);
            userRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(
                "Không xóa vĩnh viễn được: tài khoản này đã phát sinh dữ liệu trong hệ thống (ca làm, "
                + "công việc, bản ghi do người này tạo hoặc sửa…). "
                + (user.isTerminated()
                    ? "Tài khoản vẫn giữ ở trạng thái Đã nghỉ việc để tra cứu lịch sử."
                    : "Hãy dùng \"Cho nghỉ việc\" để ngừng tài khoản mà vẫn giữ lịch sử."));
        }
        log.info("Xóa vĩnh viễn tài khoản {} {}", user.getRole(), user.getEmail());
    }

    /** Cấp lại mật khẩu tạm. Trả về đúng một lần, không gửi email (BR-USER-03). */
    @Transactional
    public TempPasswordResponse resetUserPassword(UUID id) {
        User user = getOwnedUser(id);
        assertCanModify(user);
        if (user.isTerminated()) {
            throw new BusinessException("Không cấp lại mật khẩu cho nhân viên đã nghỉ việc.");
        }

        String tempPassword = generateTempPassword();
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        user.setMustChangePassword(true);
        userRepository.save(user);
        eventPublisher.publishEvent(new AccountCredentialsIssuedEvent(
            user.getEmail(), user.getFullName(), tempPassword, true));

        log.info("Cấp lại mật khẩu tạm cho {}", user.getEmail());
        return new TempPasswordResponse(UserResponse.fromEntity(user), tempPassword);
    }

    // ── Nội bộ ──────────────────────────────────────────────────────────────

    /** Xóa mềm + gỡ ca/task tương lai — phần chung của mọi trường hợp nghỉ việc. */
    private void terminate(User user) {
        user.setStatus(UserStatus.TERMINATED);
        user.setTerminatedAt(LocalDateTime.now());
        user.setTerminatedBy(SecurityUtils.getCurrentUserId());
        // Flush ngay: chốt "1 Location 1 Manager" ngay sau đó phải thấy người này đã nghỉ.
        userRepository.saveAndFlush(user);

        // BR-SCH-17 + BR-HK-07: ca và task dọn cùng dùng MỘT mốc "hôm nay" theo múi giờ
        // Location, nếu không hai bên sẽ lệch nhau quanh thời điểm giao ngày.
        LocalDate today = todayAtLocationOf(user);
        int released = releaseFutureShifts(user, today, UnassignedReason.TERMINATION);
        int releasedTasks = housekeepingService.releaseFutureTasks(
            user.getId(), UnassignedReason.TERMINATION, today);
        log.info("Cho nghỉ việc {} — gỡ {} ca và {} task dọn tương lai",
            user.getEmail(), released, releasedTasks);
    }

    /**
     * Manager dự bị hợp lệ để nhận bàn giao: cùng Tenant, là MANAGER, chưa có khách sạn, đang
     * hoạt động (không nhận người đang bị khóa — khách sạn cần người đăng nhập được ngay).
     */
    private User getReserveManager(UUID id, UUID leavingManagerId) {
        User reserve = userRepository.findByIdAndTenantId(id, SecurityUtils.getCurrentTenantId())
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        if (reserve.getId().equals(leavingManagerId)
                || reserve.getRole() != Role.MANAGER
                || reserve.getLocationId() != null
                || reserve.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("Người nhận bàn giao phải là Manager dự bị đang hoạt động.");
        }
        return reserve;
    }

    /** Khách sạn thuộc Tenant hiện tại và đang chưa có Manager (BR-SCH-08: 1 Location 1 Manager). */
    private Location getOwnedLocationWithoutManager(UUID locationId) {
        Location location = locationRepository.findByIdAndTenantId(locationId, SecurityUtils.getCurrentTenantId())
            .orElseThrow(() -> new ResourceNotFoundException("Location", "id", locationId));
        if (userRepository.existsByLocationIdAndRoleAndStatusNot(
                location.getId(), Role.MANAGER, UserStatus.TERMINATED)) {
            throw new BusinessException("Location này đã có Manager.");
        }
        return location;
    }

    /** Gán Manager dự bị vào khách sạn; khách sạn có Manager thì "Đang vận hành" (BR-ORG-02, DM-13). */
    private void assignManagerToLocation(User manager, Location location) {
        if (manager.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("Mở khóa tài khoản trước khi gán khách sạn.");
        }
        manager.setLocationId(location.getId());
        userRepository.save(manager);
        location.setStatus(LocationStatus.OPERATIONAL);
        locationRepository.save(location);
    }

    /**
     * BR-SCH-17: "ca tương lai" là ca có ngày LỚN HƠN hôm nay — ca của chính hôm nay
     * KHÔNG bị gỡ tự động. "Hôm nay" tính theo MÚI GIỜ CỦA LOCATION người này trực thuộc,
     * không theo giờ server và cũng không cố định giờ Hà Nội: một chuỗi khách sạn trải
     * nhiều múi giờ thì mốc sang ngày ở mỗi cơ sở là khác nhau.
     */
    private int releaseFutureShifts(User staff, LocalDate today, UnassignedReason reason) {
        List<Shift> futureShifts =
            shiftRepository.findByStaffIdAndShiftDateGreaterThan(staff.getId(), today);
        LocalDateTime now = LocalDateTime.now();
        for (Shift shift : futureShifts) {
            shift.setStaffId(null);
            shift.setUnassignedReason(reason);
            shift.setUnassignedAt(now);
        }
        shiftRepository.saveAll(futureShifts);
        return futureShifts.size();
    }

    /** "Hôm nay" theo múi giờ Location của nhân viên — BR-SCH-17. Không có Location thì lấy giờ Hà Nội. */
    private LocalDate todayAtLocationOf(User staff) {
        if (staff.getLocationId() == null) {
            return ShiftTimeUtils.todayInHanoi();
        }
        return locationRepository.findById(staff.getLocationId())
            .map(location -> ShiftTimeUtils.todayAt(location.getTimezone()))
            .orElseGet(ShiftTimeUtils::todayInHanoi);
    }

    private User getOwnedUser(UUID id) {
        User user = userRepository.findByIdAndTenantId(id, SecurityUtils.getCurrentTenantId())
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        // BR-PERM-03: Manager chỉ thao tác trong Location của mình.
        if (SecurityUtils.hasRole(Role.MANAGER)
                && !SecurityUtils.getCurrentLocationId().equals(user.getLocationId())) {
            throw new ResourceNotFoundException("User", "id", id);
        }
        return user;
    }

    /**
     * Ai được GHI (tạo / sửa / cho nghỉ việc / cấp lại mật khẩu) tài khoản nào — BR-PERM-02/03:
     * <pre>
     *   DIRECTOR : CRUD Manager. Với Staff chỉ "View Staff" — tạo, sửa, cho nghỉ việc Staff
     *              là việc của Manager (BR-USER-03; BR-USER-04 "không cần Giám đốc duyệt").
     *   MANAGER  : CRUD Staff trong Location của mình. Tài khoản Manager — kể cả của chính
     *              mình — do Giám đốc quản lý; thiếu chốt này Manager có thể tự cho mình nghỉ
     *              việc hoặc tự khóa mình, để lại Location không người quản lý.
     * </pre>
     * Thao tác XEM không đi qua hàm này: Giám đốc vẫn xem được toàn bộ nhân sự trong Tenant.
     */
    private static void assertCanManage(Role actor, Role target) {
        if (actor == Role.DIRECTOR && target != Role.MANAGER) {
            throw new BusinessException(
                "Giám đốc chỉ quản lý tài khoản Manager; với Staff chỉ được xem.");
        }
        if (actor == Role.MANAGER && target != Role.STAFF) {
            throw new BusinessException("Manager chỉ thao tác được trên tài khoản STAFF.");
        }
    }

    private void assertCanModify(User target) {
        assertCanManage(SecurityUtils.getCurrentRole(), target.getRole());
    }

    private void assertCanCreateRole(Role target) {
        if (target == Role.PLATFORM_ADMIN || target == Role.DIRECTOR) {
            throw new BusinessException(
                "Không tạo được tài khoản " + target + " qua luồng này.");
        }
        assertCanManage(SecurityUtils.getCurrentRole(), target);
    }

    /**
     * Trường bắt buộc khác nhau theo vai trò — BR-USER-01, BR-USER-05.
     *
     * @return Location của người được tạo, đã xác nhận thuộc đúng Tenant.
     */
    private Location validateProfile(CreateUserRequest request, UUID tenantId) {
        if (request.getRole() == Role.STAFF) {
            requireStaffProfile(request);
            if (request.getLocationId() == null) {
                throw missing("Location trực thuộc");
            }
            if (request.getPositionId() == null) {
                throw new BusinessException("Position là bắt buộc với STAFF.");
            }
            assertPositionSelectable(request.getPositionId(), tenantId);
        } else if (request.getRole() == Role.MANAGER) {
            // Location để trống = Manager dự bị, gán khách sạn sau.
            requireStaffProfile(request);
            if (request.getPositionId() != null) {
                throw new BusinessException("Manager KHÔNG có Position.");
            }
        }

        if (request.getRole() != Role.STAFF
                && request.getExtraPositionIds() != null && !request.getExtraPositionIds().isEmpty()) {
            throw new BusinessException("Chỉ STAFF mới kiêm nhiệm được nhiều vị trí.");
        }

        if (request.getRole() != Role.STAFF && request.getRole() != Role.MANAGER
                && request.getLocationId() != null) {
            throw new BusinessException("Vai trò này không gắn với Location nào.");
        }

        // Manager chỉ tạo người trong chính Location của mình.
        if (SecurityUtils.hasRole(Role.MANAGER)
                && !SecurityUtils.getCurrentLocationId().equals(request.getLocationId())) {
            throw new BusinessException("Chỉ tạo được nhân sự trong Location của mình.");
        }

        if (request.getLocationId() == null) {
            return null;
        }
        // Khóa ngoại chỉ đảm bảo Location TỒN TẠI, không đảm bảo nó thuộc Tenant này.
        Location location = locationRepository.findByIdAndTenantId(request.getLocationId(), tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Location", "id", request.getLocationId()));

        // BR-SCH-08: 1 Location chỉ có 1 Manager.
        if (request.getRole() == Role.MANAGER
                && userRepository.existsByLocationIdAndRoleAndStatusNot(
                    location.getId(), Role.MANAGER, UserStatus.TERMINATED)) {
            throw new BusinessException("Location này đã có Manager.");
        }
        return location;
    }

    /**
     * Các trường hồ sơ bắt buộc của BR-USER-01 mà Staff và Manager dùng chung. Location và
     * Position kiểm riêng ở {@link #validateProfile}: Manager dự bị không có Location.
     */
    private void requireStaffProfile(CreateUserRequest request) {
        if (request.getStartWorkDate() == null) throw missing("Ngày bắt đầu làm việc");
        if (request.getDateOfBirth() == null)   throw missing("Ngày sinh");
        if (request.getGender() == null)        throw missing("Giới tính");
        if (isBlank(request.getAddress()))      throw missing("Địa chỉ");
        if (isBlank(request.getAvatarUrl()))    throw missing("Ảnh đại diện");
        assertStartWorkDateAfterToday(request.getStartWorkDate());
    }

    /** Ngày bắt đầu làm việc phải SAU hôm nay (giờ Hà Nội) — hôm nay cũng không được. */
    private static void assertStartWorkDateAfterToday(LocalDate startWorkDate) {
        LocalDate today = ShiftTimeUtils.todayInHanoi();
        if (!startWorkDate.isAfter(today)) {
            throw new BusinessException(String.format(
                "Ngày bắt đầu làm việc phải sau ngày hôm nay (%s).", today));
        }
    }

    /** Position thuộc Tenant và chưa bị ẩn — BR-ORG-14: mục đã ẩn không còn được chọn. */
    private void assertPositionSelectable(UUID positionId, UUID tenantId) {
        Position position = positionRepository.findByIdAndTenantId(positionId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Position", "id", positionId));
        if (!position.isActive()) {
            throw new BusinessException("Vị trí \"" + position.getName() + "\" đã ngừng sử dụng, hãy chọn vị trí khác.");
        }
    }

    /**
     * Vị trí kiêm nhiệm của nhân viên đa nhiệm. Bỏ trùng lặp và bỏ chính vị trí chính. Mục MỚI thêm
     * phải thuộc Tenant và chưa bị ẩn (BR-ORG-14); mục đang giữ từ trước thì giữ tiếp được, giống
     * cách xử lý vị trí chính ở {@link #updateUser}.
     */
    private Set<UUID> resolveExtraPositions(List<UUID> requested, UUID primaryPositionId,
                                            Set<UUID> alreadyHeld, UUID tenantId) {
        Set<UUID> result = new LinkedHashSet<>();
        if (requested == null) {
            return result;
        }
        for (UUID positionId : requested) {
            if (positionId == null || positionId.equals(primaryPositionId) || result.contains(positionId)) {
                continue;
            }
            if (!alreadyHeld.contains(positionId)) {
                assertPositionSelectable(positionId, tenantId);
            }
            result.add(positionId);
        }
        return result;
    }

    /**
     * BR-SAAS-02, BR-SAAS-03: số Staff bị chặn bởi quota User của gói; Giám đốc và Manager không
     * tính. Người đã nghỉ việc không chiếm suất. Cách hiểu dùng thử giống quota Location
     * ({@code LocationService}): vẫn theo quota của gói đã chọn (BR-SAAS-08).
     *
     * <p>Đọc gói kèm khóa ghi để hai Manager cùng tạo Staff một lúc không cùng lọt qua bước đếm.
     */
    private void assertStaffQuotaAvailable(UUID tenantId) {
        Subscription subscription = subscriptionRepository.findForUpdateByTenantId(tenantId)
            .orElseThrow(() -> new BusinessException("Tenant chưa có gói dịch vụ nên chưa tạo được nhân viên."));

        long used = userRepository.countByTenantIdAndRoleAndStatusNot(tenantId, Role.STAFF, UserStatus.TERMINATED);
        if (used >= subscription.getQuotaUser()) {
            throw new BusinessException(String.format(
                "Đã dùng hết %d/%d suất nhân viên của gói dịch vụ. Liên hệ Giám đốc nâng cấp gói để thêm nhân viên.",
                used, subscription.getQuotaUser()));
        }
    }

    private static BusinessException missing(String field) {
        return new BusinessException(field + " là bắt buộc.");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String generateTempPassword() {
        return RandomStringUtils.randomAlphanumeric(TEMP_PASSWORD_LENGTH);
    }
}
