package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.LocationStatus;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.UserStatus;
import com.example.SWP391_G2_SE2055_JV.repository.LocationRepository;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import com.example.SWP391_G2_SE2055_JV.utils.ShiftTimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trạng thái vận hành của chi nhánh — BR-ORG-02, DM-13, và chốt 06/10/2026 (ghi vào THAY_DOI_BR):
 * chi nhánh chỉ "Đang vận hành" từ NGÀY BẮT ĐẦU LÀM VIỆC của quản lý được giao. Có quản lý nhưng chưa
 * tới ngày thì vẫn "Chưa vận hành"; tới đúng ngày đó thì {@link LocationActivationJob} tự bật.
 *
 * <p>"Ngày" tính theo múi giờ của chính chi nhánh (BR-SCH-17), cùng mốc với lịch làm việc.
 *
 * <p>Nơi DUY NHẤT quyết định trạng thái theo quản lý: {@code UserService} gọi {@link #apply} mỗi khi
 * gán quản lý cho chi nhánh hoặc đổi ngày bắt đầu làm của quản lý đang phụ trách.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationActivationService {

    private final LocationRepository locationRepository;
    private final UserRepository     userRepository;

    /**
     * Đặt lại trạng thái của {@code location} theo quản lý đang phụ trách nó: tới ngày bắt đầu làm thì
     * "Đang vận hành", chưa tới thì "Chưa vận hành".
     */
    public void apply(Location location, User manager) {
        LocationStatus status = hasStarted(manager, location)
            ? LocationStatus.OPERATIONAL
            : LocationStatus.NOT_OPERATIONAL;
        if (location.getStatus() != status) {
            location.setStatus(status);
            locationRepository.save(location);
            log.info("Chi nhánh {} → {} (quản lý {} bắt đầu làm {})",
                location.getId(), status, manager.getEmail(), manager.getStartWorkDate());
        }
    }

    /**
     * Bật mọi chi nhánh "Chưa vận hành" có quản lý đã tới ngày bắt đầu làm — chạy định kỳ, mọi Tenant.
     * Idempotent: chạy lại nhiều lần hay nhiều instance cùng chạy thì kết quả không đổi.
     *
     * @return số chi nhánh vừa được bật
     */
    @Transactional
    public int activateDueLocations() {
        int activated = 0;
        for (Location location : locationRepository.findByStatus(LocationStatus.NOT_OPERATIONAL)) {
            User manager = userRepository
                .findFirstByLocationIdAndRoleAndStatusNot(location.getId(), Role.MANAGER, UserStatus.TERMINATED)
                .orElse(null);
            if (manager != null && hasStarted(manager, location)) {
                location.setStatus(LocationStatus.OPERATIONAL);
                activated++;
                log.info("Chi nhánh {} bắt đầu vận hành — tới ngày quản lý {} bắt đầu làm ({})",
                    location.getId(), manager.getEmail(), manager.getStartWorkDate());
            }
        }
        return activated;
    }

    /**
     * Quản lý đã tới ngày bắt đầu làm ở chi nhánh chưa. Dữ liệu cũ không có ngày (trước khi bắt buộc
     * nhập) coi như đã bắt đầu — không tắt chi nhánh đang chạy chỉ vì thiếu dữ liệu.
     */
    static boolean hasStarted(User manager, Location location) {
        return manager.getStartWorkDate() == null
            || !manager.getStartWorkDate().isAfter(ShiftTimeUtils.todayAt(location.getTimezone()));
    }
}
