package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.CreateUserRequest;
import com.example.SWP391_G2_SE2055_JV.dto.TempPasswordResponse;
import com.example.SWP391_G2_SE2055_JV.dto.UpdateUserRequest;
import com.example.SWP391_G2_SE2055_JV.dto.UserResponse;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.Shift;
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
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import com.example.SWP391_G2_SE2055_JV.utils.ShiftTimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
    private final HousekeepingService housekeepingService;
    private final ApplicationEventPublisher eventPublisher;
    private final PasswordEncoder    passwordEncoder;

    @Transactional(readOnly = true)
    public Page<UserResponse> getUsers(Pageable pageable) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        // BR-PERM-03: Manager chỉ thấy nhân sự trong Location của mình.
        if (SecurityUtils.hasRole(Role.MANAGER)) {
            return userRepository
                .findByTenantIdAndLocationId(tenantId, SecurityUtils.getCurrentLocationId(), pageable)
                .map(UserResponse::fromEntity);
        }
        return userRepository.findByTenantId(tenantId, pageable).map(UserResponse::fromEntity);
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

        // BR-USER-06: email của người đã nghỉ việc VẪN chiếm chỗ, không dùng lại được.
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email đã tồn tại trong hệ thống: " + request.getEmail());
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

        // BR-ORG-02, DM-13: Location có Manager thì mới chính thức vận hành.
        if (saved.getRole() == Role.MANAGER) {
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

        if (request.getFullName() != null)      user.setFullName(request.getFullName());
        if (request.getPhone() != null)         user.setPhone(request.getPhone());
        if (request.getStartWorkDate() != null) user.setStartWorkDate(request.getStartWorkDate());
        if (request.getDateOfBirth() != null)   user.setDateOfBirth(request.getDateOfBirth());
        if (request.getGender() != null)        user.setGender(request.getGender());
        if (request.getAddress() != null)       user.setAddress(request.getAddress());
        if (request.getAvatarUrl() != null)     user.setAvatarUrl(request.getAvatarUrl());

        if (request.getPositionId() != null) {
            if (!user.isStaff()) {
                throw new BusinessException("Chỉ STAFF mới có Position.");
            }
            assertPositionExists(request.getPositionId(), user.getTenantId());
            user.setPositionId(request.getPositionId());
        }

        if (request.getEnabled() != null) {
            user.setStatus(request.getEnabled() ? UserStatus.ACTIVE : UserStatus.INACTIVE);
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
     */
    @Transactional
    public UserResponse terminateUser(UUID id) {
        User user = getOwnedUser(id);
        assertCanModify(user);

        if (user.isTerminated()) {
            throw new BusinessException("Nhân viên này đã ở trạng thái nghỉ việc.");
        }
        if (user.getRole() == Role.DIRECTOR) {
            throw new BusinessException("Không cho nghỉ việc tài khoản Giám đốc qua luồng này.");
        }

        user.setStatus(UserStatus.TERMINATED);
        user.setTerminatedAt(LocalDateTime.now());
        user.setTerminatedBy(SecurityUtils.getCurrentUserId());
        userRepository.save(user);

        // BR-SCH-17 + BR-HK-07: ca và task dọn cùng dùng MỘT mốc "hôm nay" theo múi giờ
        // Location, nếu không hai bên sẽ lệch nhau quanh thời điểm giao ngày.
        LocalDate today = todayAtLocationOf(user);
        int released = releaseFutureShifts(user, today, UnassignedReason.TERMINATION);
        int releasedTasks = housekeepingService.releaseFutureTasks(
            user.getId(), UnassignedReason.TERMINATION, today);
        log.info("Cho nghỉ việc {} — gỡ {} ca và {} task dọn tương lai",
            user.getEmail(), released, releasedTasks);

        // DM-13: Location mất Manager thì quay về "Chưa vận hành" cho tới khi có Manager mới.
        if (user.getRole() == Role.MANAGER && user.getLocationId() != null) {
            locationRepository.findById(user.getLocationId()).ifPresent(location -> {
                location.setStatus(LocationStatus.NOT_OPERATIONAL);
                locationRepository.save(location);
            });
        }

        return UserResponse.fromEntity(user);
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
     * BR-PERM-02/03: Manager chỉ quản lý STAFF; tài khoản Manager — kể cả của chính mình —
     * do Giám đốc quản lý. Thiếu chốt này Manager có thể tự cho mình nghỉ việc hoặc tự
     * khóa mình, để lại Location không người quản lý.
     */
    private void assertCanModify(User target) {
        if (SecurityUtils.hasRole(Role.MANAGER) && target.getRole() != Role.STAFF) {
            throw new BusinessException("Manager chỉ thao tác được trên tài khoản STAFF.");
        }
    }

    /** BR-PERM-02/03: Giám đốc CRUD Manager, Manager CRUD Staff trong Location. */
    private void assertCanCreateRole(Role target) {
        Role actor = SecurityUtils.getCurrentRole();

        if (target == Role.PLATFORM_ADMIN || target == Role.DIRECTOR) {
            throw new BusinessException(
                "Không tạo được tài khoản " + target + " qua luồng này.");
        }
        if (actor == Role.MANAGER && target != Role.STAFF) {
            throw new BusinessException("Manager chỉ được tạo tài khoản STAFF.");
        }
    }

    /**
     * Trường bắt buộc khác nhau theo vai trò — BR-USER-01, BR-USER-05.
     *
     * @return Location của người được tạo, đã xác nhận thuộc đúng Tenant.
     */
    private Location validateProfile(CreateUserRequest request, UUID tenantId) {
        if (request.getRole() == Role.STAFF) {
            requireStaffProfile(request);
            if (request.getPositionId() == null) {
                throw new BusinessException("Position là bắt buộc với STAFF.");
            }
            assertPositionExists(request.getPositionId(), tenantId);
        } else if (request.getRole() == Role.MANAGER) {
            requireStaffProfile(request);
            if (request.getPositionId() != null) {
                throw new BusinessException("Manager KHÔNG có Position.");
            }
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

    /** Bộ 10 trường bắt buộc của BR-USER-01 (Manager dùng lại, trừ Position). */
    private void requireStaffProfile(CreateUserRequest request) {
        if (request.getLocationId() == null)    throw missing("Location trực thuộc");
        if (request.getStartWorkDate() == null) throw missing("Ngày bắt đầu làm việc");
        if (request.getDateOfBirth() == null)   throw missing("Ngày sinh");
        if (request.getGender() == null)        throw missing("Giới tính");
        if (isBlank(request.getAddress()))      throw missing("Địa chỉ");
        if (isBlank(request.getAvatarUrl()))    throw missing("Ảnh đại diện");
    }

    private void assertPositionExists(UUID positionId, UUID tenantId) {
        positionRepository.findByIdAndTenantId(positionId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Position", "id", positionId));
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
