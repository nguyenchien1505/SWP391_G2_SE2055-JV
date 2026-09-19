package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.SchedulePolicyResponse;
import com.example.SWP391_G2_SE2055_JV.dto.UpdateSchedulePolicyRequest;
import com.example.SWP391_G2_SE2055_JV.entity.SchedulePolicy;
import com.example.SWP391_G2_SE2055_JV.repository.SchedulePolicyRepository;
import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Schedule Policy — BR-SCH-01: cấu hình ở cấp TENANT, dùng chung một bộ quy tắc cho
 * mọi Location, do Giám đốc quản lý.
 *
 * <p>DM-18: mỗi Tenant đúng 1 bản ghi, sửa đè trực tiếp, không lưu lịch sử phiên bản
 * — vì vậy API chỉ có GET và PUT, không có danh sách và không có xóa.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulePolicyService {

    private final SchedulePolicyRepository policyRepository;

    @Transactional
    public SchedulePolicyResponse getPolicy() {
        return SchedulePolicyResponse.fromEntity(getOrCreate(SecurityUtils.getCurrentTenantId()));
    }

    @Transactional
    public SchedulePolicyResponse updatePolicy(UpdateSchedulePolicyRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        SchedulePolicy policy = getOrCreate(tenantId);

        policy.setMaxHoursPerDay(request.getMaxHoursPerDay());
        policy.setMaxHoursPerWeek(request.getMaxHoursPerWeek());
        policy.setMaxConsecutiveShifts(request.getMaxConsecutiveShifts());
        policy.setMinRestHoursBetweenShifts(request.getMinRestHoursBetweenShifts());
        policy.setMinDaysOffPerWeek(request.getMinDaysOffPerWeek());
        policy.setSwapResponseTimeoutHours(request.getSwapResponseTimeoutHours());

        log.info("Cập nhật Schedule Policy của Tenant {}", tenantId);
        // Giá trị mới chỉ áp cho ca xếp từ đây trở đi; ca đã xếp giữ nguyên (BR-SCH-20).
        return SchedulePolicyResponse.fromEntity(policyRepository.save(policy));
    }

    /**
     * BR-SCH-20: Tenant mới phải được sinh sẵn policy mặc định. Việc đó thuộc luồng
     * đăng ký Tenant; ở đây tự tạo bản mặc định để không Tenant nào rơi vào trạng
     * thái "chưa cấu hình" và chặn oan việc xếp ca.
     */
    private SchedulePolicy getOrCreate(UUID tenantId) {
        return policyRepository.findByTenantId(tenantId)
            .orElseGet(() -> {
                log.warn("Tenant {} chưa có Schedule Policy — tạo bản mặc định theo BR-SCH-20", tenantId);
                return policyRepository.save(SchedulePolicy.builder().tenantId(tenantId).build());
            });
    }
}
