package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.RegisterTenantRequest;
import com.example.SWP391_G2_SE2055_JV.dto.RegisterTenantResponse;
import com.example.SWP391_G2_SE2055_JV.entity.PricingConfig;
import com.example.SWP391_G2_SE2055_JV.entity.SchedulePolicy;
import com.example.SWP391_G2_SE2055_JV.entity.Subscription;
import com.example.SWP391_G2_SE2055_JV.entity.Tenant;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.TenantStatus;
import com.example.SWP391_G2_SE2055_JV.enums.UserStatus;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.repository.SchedulePolicyRepository;
import com.example.SWP391_G2_SE2055_JV.repository.SubscriptionRepository;
import com.example.SWP391_G2_SE2055_JV.repository.TenantRepository;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import com.example.SWP391_G2_SE2055_JV.utils.ShiftTimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Tenant tự đăng ký — BR-SAAS-13.
 *
 * <p>Một lần đăng ký tạo ĐỒNG THỜI 4 bản ghi trong CÙNG một transaction, nên hoặc đủ cả 4
 * hoặc không có gì (không để lại Tenant mồ côi khi một bước sau lỗi):
 * <pre>
 *   1. tenants            status = TRIAL                                   (BR-SAAS-01, 13)
 *   2. users              role = DIRECTOR, email = email đăng ký           (BR-USER-05, BR-PERM-02)
 *   3. subscriptions      gói dùng thử, hạn = hôm nay + trialDays          (BR-SAAS-05, 08, DM-09)
 *   4. schedule_policies  bộ mặc định                                      (BR-SCH-20, DM-18)
 * </pre>
 *
 * <p>Đây là endpoint CÔNG KHAI (người đăng ký chưa có tài khoản), nên service này tuyệt đối
 * không gọi {@code SecurityUtils.getCurrentTenantId()} — người gọi là ẩn danh, hàm đó sẽ ném
 * lỗi. Các cột {@code created_by/updated_by} vì thế để NULL (xem {@code JpaConfig}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantRegistrationService {

    /**
     * Quota của gói dùng thử. LƯU Ý: BR-SAAS-08 ghi trong thời gian dùng thử "không giới hạn
     * riêng số lượng", nhưng team chốt dùng 3/20/50 theo dữ liệu seed. Cần cập nhật BR nếu
     * giữ con số này. Muốn đổi mà không sửa code thì chuyển sang cấu hình application.yaml.
     */
    private static final int TRIAL_QUOTA_LOCATION = 1;
    private static final int TRIAL_QUOTA_USER     = 20;
    private static final int TRIAL_QUOTA_ROOM     = 50;

    private final TenantRepository         tenantRepository;
    private final UserRepository           userRepository;
    private final SubscriptionRepository   subscriptionRepository;
    private final SchedulePolicyRepository schedulePolicyRepository;
    private final PlatformConfigService    platformConfigService;
    private final PasswordEncoder          passwordEncoder;

    @Transactional
    public RegisterTenantResponse registerTenant(RegisterTenantRequest request) {
        // Chuẩn hóa về một dạng để lưu và so khớp đồng nhất. DB dùng collation không phân
        // biệt hoa thường nên "A@x.com" và "a@x.com" vốn đã bị coi là trùng.
        String email = request.getEmail().trim().toLowerCase();

        // BR-USER-06: email là username, unique TOÀN HỆ THỐNG. Kiểm tra cả bảng tenants vì
        // contact_email cũng UNIQUE. Đây là kiểm tra để trả thông báo rõ ràng; nếu hai
        // request cùng email chạy song song thì UNIQUE ở DB vẫn chặn một request và
        // GlobalExceptionHandler đổi lỗi đó thành 409.
        if (userRepository.existsByEmail(email) || tenantRepository.existsByContactEmail(email)) {
            throw new BusinessException("Email đã được sử dụng: " + email);
        }

        // Hai hàm đọc của F1: số ngày dùng thử (BR-SAAS-08) và bảng giá đang hiệu lực (BR-SAAS-05).
        // "Hôm nay" theo giờ Hà Nội — quy ước team, không dùng LocalDate.now() trần.
        LocalDate    today   = ShiftTimeUtils.todayInHanoi();
        int          trialDays = platformConfigService.loadSystemConfig().getTrialDays();
        PricingConfig pricing = platformConfigService.resolvePricing(today);

        // 1. Tenant. Không set suspendReason: chỉ có ý nghĩa khi SUSPENDED (BR-SAAS-16).
        Tenant tenant = tenantRepository.save(Tenant.builder()
            .name(request.getCompanyName().trim())
            .contactEmail(email)
            .contactPhone(request.getPhone())
            .status(TenantStatus.TRIAL)
            .build());

        // 2. Giám đốc. Hồ sơ tối thiểu (BR-USER-05): không Location, không Position.
        //    Mật khẩu do chính người đăng ký đặt nên must_change_password = false —
        //    BR-USER-07 chỉ bắt đổi với mật khẩu TẠM do hệ thống cấp.
        User director = userRepository.save(User.builder()
            .tenantId(tenant.getId())
            .role(Role.DIRECTOR)
            .email(email)
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .mustChangePassword(false)
            .status(UserStatus.ACTIVE)
            .fullName(request.getRepresentativeName().trim())
            .phone(request.getPhone())
            .build());

        // 3. Gói dùng thử. Ba cột giá là SNAPSHOT: Admin đổi bảng giá sau này không ảnh
        //    hưởng gói đã chốt (BR-SAAS-05). Chu kỳ thanh toán để NULL cho tới khi trả phí.
        LocalDate trialEndsAt = today.plusDays(trialDays);
        subscriptionRepository.save(Subscription.builder()
            .tenantId(tenant.getId())
            .quotaLocation(TRIAL_QUOTA_LOCATION)
            .quotaUser(TRIAL_QUOTA_USER)
            .quotaRoom(TRIAL_QUOTA_ROOM)
            .pricePerLocation(pricing.getPricePerLocation())
            .pricePerUser(pricing.getPricePerUser())
            .pricePerRoom(pricing.getPricePerRoom())
            .trial(true)
            .trialEndsAt(trialEndsAt)
            .build());

        // 4. Policy mặc định (BR-SCH-20). Các giá trị 8h/48h/6/12h/1/24h nằm ở @Builder.Default
        //    của entity. Lưu bằng repository trực tiếp, không qua SchedulePolicyService.getOrCreate,
        //    vì hàm đó ghi log WARN "Tenant chưa có Policy" — gây nhầm ở luồng hợp lệ này.
        schedulePolicyRepository.save(SchedulePolicy.builder()
            .tenantId(tenant.getId())
            .build());

        log.info("Đăng ký Tenant mới {} ({}), Giám đốc {}, dùng thử đến {}",
            tenant.getId(), tenant.getName(), email, trialEndsAt);

        return RegisterTenantResponse.builder()
            .tenantId(tenant.getId())
            .companyName(tenant.getName())
            .status(tenant.getStatus())
            .trialEndsAt(trialEndsAt)
            .directorId(director.getId())
            .directorEmail(director.getEmail())
            .quotaLocation(TRIAL_QUOTA_LOCATION)
            .quotaUser(TRIAL_QUOTA_USER)
            .quotaRoom(TRIAL_QUOTA_ROOM)
            .build();
    }
}
