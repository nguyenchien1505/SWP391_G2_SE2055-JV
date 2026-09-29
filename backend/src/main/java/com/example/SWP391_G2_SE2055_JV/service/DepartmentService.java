package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.CatalogUsageResponse;
import com.example.SWP391_G2_SE2055_JV.dto.DepartmentRequest;
import com.example.SWP391_G2_SE2055_JV.dto.DepartmentResponse;
import com.example.SWP391_G2_SE2055_JV.entity.Department;
import com.example.SWP391_G2_SE2055_JV.entity.Position;
import com.example.SWP391_G2_SE2055_JV.enums.UserStatus;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.DepartmentRepository;
import com.example.SWP391_G2_SE2055_JV.repository.PositionRepository;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phòng ban — BR-ORG-06, BR-ORG-10, BR-ORG-13, BR-ORG-14.
 *
 * <p>Danh mục cấp TENANT, dùng chung cho mọi Location: chỉ Giám đốc tạo, Manager chỉ đọc
 * để chọn khi tạo nhân sự (BR-ORG-06).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final PositionRepository   positionRepository;
    private final UserRepository       userRepository;

    /**
     * @param includeInactive true để Giám đốc thấy cả mục đã ẩn khi quản trị danh mục;
     *                        false (mặc định) cho danh sách chọn — BR-ORG-14.
     */
    @Transactional(readOnly = true)
    public Page<DepartmentResponse> getDepartments(boolean includeInactive, Pageable pageable) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Page<Department> page = includeInactive
            ? departmentRepository.findByTenantId(tenantId, pageable)
            : departmentRepository.findByTenantIdAndActiveTrue(tenantId, pageable);

        return page.map(DepartmentResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public DepartmentResponse getDepartmentById(UUID id) {
        return DepartmentResponse.fromEntity(getOwnedDepartment(id));
    }

    @Transactional
    public DepartmentResponse createDepartment(DepartmentRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        String name = request.getName().trim();

        // BR-ORG-13: unique trong phạm vi Tenant, không phải toàn hệ thống.
        if (departmentRepository.existsByTenantIdAndName(tenantId, name)) {
            throw new BusinessException("Phòng ban đã tồn tại trong khách sạn của bạn: " + name);
        }

        Department department = departmentRepository.save(
            Department.builder().tenantId(tenantId).name(name).build());

        log.info("Tạo Phòng ban {} cho Tenant {}", name, tenantId);
        return DepartmentResponse.fromEntity(department);
    }

    @Transactional
    public DepartmentResponse updateDepartment(UUID id, DepartmentRequest request) {
        Department department = getOwnedDepartment(id);
        String name = request.getName().trim();

        if (departmentRepository.existsByTenantIdAndNameAndIdNot(
                department.getTenantId(), name, department.getId())) {
            throw new BusinessException("Phòng ban đã tồn tại trong khách sạn của bạn: " + name);
        }

        department.setName(name);
        return DepartmentResponse.fromEntity(departmentRepository.save(department));
    }

    /** BR-ORG-14: ẩn khỏi danh sách chọn thay cho việc xóa, vì BR-ORG-10 chặn cứng xóa. */
    @Transactional
    public DepartmentResponse setActive(UUID id, boolean active) {
        Department department = getOwnedDepartment(id);
        department.setActive(active);

        log.info("{} Phòng ban {}", active ? "Hiện" : "Ẩn", department.getId());
        return DepartmentResponse.fromEntity(departmentRepository.save(department));
    }

    /**
     * BR-ORG-10: chặn cứng khi còn tham chiếu, không ngoại lệ.
     *
     * <p>Nhân viên không trỏ thẳng vào Department mà qua Position (BR-ORG-07), nên chặn
     * ngay ở mức "còn Position trỏ vào" — đây cũng đúng là ràng buộc khóa ngoại của DB.
     * Muốn ngừng dùng thì ẩn bằng {@link #setActive} (BR-ORG-14).
     */
    @Transactional
    public void deleteDepartment(UUID id) {
        Department department = getOwnedDepartment(id);

        if (positionRepository.existsByDepartmentId(department.getId())) {
            throw new BusinessException(
                "Không xóa được Phòng ban: vẫn còn chức danh thuộc phòng ban này. "
                + "Hãy ẩn phòng ban thay vì xóa.");
        }

        departmentRepository.delete(department);
        log.info("Xóa Phòng ban {}", department.getId());
    }

    /**
     * Các vị trí thuộc phòng ban này — đúng tập mà {@link #deleteDepartment} dựa vào để chặn xóa —
     * kèm số nhân viên đang làm / đã nghỉ việc của từng vị trí (đếm gộp một câu).
     */
    @Transactional(readOnly = true)
    public CatalogUsageResponse getUsage(UUID id) {
        Department department = getOwnedDepartment(id);
        List<Position> positions = positionRepository.findByDepartmentId(department.getId());

        Map<UUID, long[]> counts = new HashMap<>(); // [đang làm, đã nghỉ việc]
        if (!positions.isEmpty()) {
            userRepository.countStaffByPositionAndStatus(positions.stream().map(Position::getId).toList())
                .forEach(row -> counts.computeIfAbsent(row.getPositionId(), key -> new long[2])
                    [row.getStatus() == UserStatus.TERMINATED ? 1 : 0] += row.getTotal());
        }

        List<CatalogUsageResponse.PositionRef> refs = positions.stream()
            .map(position -> {
                long[] count = counts.getOrDefault(position.getId(), new long[2]);
                return new CatalogUsageResponse.PositionRef(position.getId(), position.getName(),
                    position.getPositionType(), position.isActive(), count[0], count[1]);
            })
            .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
            .toList();
        return CatalogUsageResponse.builder().positions(refs).build();
    }

    private Department getOwnedDepartment(UUID id) {
        return departmentRepository.findByIdAndTenantId(id, SecurityUtils.getCurrentTenantId())
            .orElseThrow(() -> new ResourceNotFoundException("Department", "id", id));
    }
}
