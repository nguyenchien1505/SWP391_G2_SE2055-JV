package com.example.SWP391_G2_SE2055_JV.config;

import com.example.SWP391_G2_SE2055_JV.entity.Tenant;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.SuspendReason;
import com.example.SWP391_G2_SE2055_JV.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * MỘT chỗ duy nhất quyết định "trạng thái của Tenant cho phép người này vào tới đâu".
 *
 * <p>Đăng nhập bằng mật khẩu, đăng nhập Google, và filter đọc lại user mỗi request đều gọi
 * cùng hàm {@link #evaluate}, nên các luồng không thể lệch nhau.
 *
 * <pre>
 *   Tenant                                        Giám đốc    Manager, Staff
 *   TRIAL / ACTIVE / PAYMENT_OVERDUE              FULL        FULL
 *   SUSPENDED do ADMIN_LOCKED (hoặc thiếu lý do)  BLOCKED     BLOCKED
 *   SUSPENDED do TRIAL_EXPIRED / PAYMENT_FAILED   READ_ONLY   BLOCKED
 *   Tenant không tồn tại                          BLOCKED     BLOCKED
 *   Admin Platform (không thuộc Tenant nào)       FULL
 * </pre>
 *
 * <p>Khi Tenant hết hạn dùng thử hoặc thanh toán thất bại, Giám đốc vẫn vào được để thanh toán
 * nhưng chỉ ở chế độ chỉ đọc; Admin Platform khóa thì chặn tất cả kể cả Giám đốc. Đây là điều
 * chỉnh so với BR-SAAS-11 ("chặn hoàn toàn, không read-only"), cần cập nhật lại tài liệu.
 *
 * <p>{@code SUSPENDED} thiếu lý do là dữ liệu lệch (DB không ép phải có) — coi như
 * ADMIN_LOCKED để mặc định là chặn, an toàn hơn cho phép.
 */
@Component
@RequiredArgsConstructor
public class TenantAccessPolicy {

    public enum Mode {
        /** Dùng đầy đủ theo phân quyền thường. */
        FULL,
        /** Đăng nhập được nhưng chỉ xem — filter chặn mọi thao tác ghi. */
        READ_ONLY,
        /** Không đăng nhập được, session đang mở bị cắt. */
        BLOCKED
    }

    /** {@code reason} chỉ có giá trị khi Tenant SUSPENDED có lý do. */
    public record Decision(Mode mode, SuspendReason reason) {}

    private final TenantRepository tenantRepository;

    @Transactional(readOnly = true)
    public Decision evaluate(User user) {
        if (user.getTenantId() == null) {
            return new Decision(Mode.FULL, null);            // PLATFORM_ADMIN — BR-PERM-01
        }

        Tenant tenant = tenantRepository.findById(user.getTenantId()).orElse(null);
        if (tenant == null) {
            return new Decision(Mode.BLOCKED, null);         // tham chiếu mồ côi: chặn cho an toàn
        }
        if (!tenant.isLoginBlocked()) {
            return new Decision(Mode.FULL, null);            // chưa bị khóa
        }

        SuspendReason reason = tenant.getSuspendReason();
        boolean payable = reason == SuspendReason.TRIAL_EXPIRED || reason == SuspendReason.PAYMENT_FAILED;
        if (payable && user.getRole() == Role.DIRECTOR) {
            return new Decision(Mode.READ_ONLY, reason);
        }
        return new Decision(Mode.BLOCKED, reason);
    }
}
