package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.config.CustomUserDetails;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.repository.LocationRepository;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftRepository;
import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import com.example.SWP391_G2_SE2055_JV.utils.ShiftTimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;

/**
 * Giám sát vận hành chỉ thao tác giám sát trong ngày có ca tại Location của mình — BR-PERM-07
 * (chốt 19/10/2026). "Thao tác" là giao việc dọn, kiểm tra phòng, đóng báo hỏng; XEM lịch dọn và
 * báo hỏng thì không giới hạn.
 *
 * <ul>
 *   <li>Có ca trong ngày là đủ, không cần đã check-in hay đang trong khung giờ ca — khác điều kiện
 *       tự nhận việc dọn (BR-HK-15).</li>
 *   <li>Ca qua đêm thuộc ngày BẮT ĐẦU ca (BR-SCH-03), nên ca 22:00–06:00 ngày D vẫn tính lúc 02:00
 *       ngày D+1 — chưa tới giờ kết thúc thì người đó vẫn đang trực. Không tính vậy thì Giám sát ca
 *       đêm bị chặn ngay giữa ca.</li>
 * </ul>
 *
 * <p>Chỉ gọi cho Giám sát ({@code SecurityUtils.isSupervisor()}): Manager không có ca làm việc.
 */
@Component
@RequiredArgsConstructor
public class SupervisorDutyRule {

    private final ShiftRepository    shiftRepository;
    private final LocationRepository locationRepository;

    /** Chặn khi Giám sát đang đăng nhập không có ca hôm nay tại khách sạn của mình. */
    public void assertOnDuty() {
        CustomUserDetails supervisor = SecurityUtils.getCurrentUser();
        UUID locationId = supervisor.getLocationId();
        // BR-SCH-17: "hôm nay" theo múi giờ của khách sạn — cùng mốc với lịch làm việc.
        String timezone = locationRepository.findByIdAndTenantId(locationId, supervisor.getTenantId())
            .map(Location::getTimezone)
            .orElse(null);
        LocalDateTime now = ShiftTimeUtils.nowAt(timezone);
        LocalDate today = now.toLocalDate();

        Collection<Shift> shifts = shiftRepository.findByStaffIdAndShiftDateBetweenOrderByShiftDateAscStartTimeAsc(
            supervisor.getId(), today.minusDays(1), today);
        if (!isOnDuty(shifts, locationId, now)) {
            throw new BusinessException("Giám sát chỉ thao tác được trong ngày có ca tại khách sạn của mình "
                + "— hôm nay bạn không có ca nào.");
        }
    }

    /**
     * Phần tính thuần, tách riêng để test được với một mốc giờ cố định.
     *
     * @param shifts ca của người đó trong hôm qua và hôm nay
     * @param now    giờ hiện tại theo múi giờ của khách sạn
     */
    static boolean isOnDuty(Collection<Shift> shifts, UUID locationId, LocalDateTime now) {
        // Điều kiện "ca thuộc ngày làm hiện tại" dùng chung với việc dọn ca đêm.
        return shifts.stream()
            .filter(shift -> locationId.equals(shift.getLocationId()))
            .anyMatch(shift -> ShiftTimeUtils.isInCurrentWorkDay(
                shift.getShiftDate(), shift.isOvernight(), shift.getEndTime(), now));
    }
}
