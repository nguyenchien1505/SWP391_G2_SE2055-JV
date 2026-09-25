package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.CreatePositionRequest;
import com.example.SWP391_G2_SE2055_JV.dto.PositionResponse;
import com.example.SWP391_G2_SE2055_JV.dto.UpdatePositionRequest;
import com.example.SWP391_G2_SE2055_JV.entity.Department;
import com.example.SWP391_G2_SE2055_JV.entity.Position;
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

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Chức danh — BR-ORG-06..10, BR-ORG-13, BR-ORG-14.
 *
 * <p>Danh mục cấp TENANT. Lễ tân và Dọn dẹp KHÔNG phải role mà là Loại Position
 * (BR-ORG-08): quyền nghiệp vụ đặc thù bám vào {@code positionType}, nên trường này được
 * bảo vệ chặt hơn tên hiển thị.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PositionService {

    private final PositionRepository   positionRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository       userRepository;

    @Transactional(readOnly = true)
    public Page<PositionResponse> getPositions(boolean includeInactive, Pageable pageable) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Page<Position> page = includeInactive
            ? positionRepository.findByTenantId(tenantId, pageable)
            : positionRepository.findByTenantIdAndActiveTrue(tenantId, pageable);

        // BR-ORG-07: màn hình luôn hiện Department suy ra từ Position — nạp gộp một lần.
        // Department là danh mục cấp Tenant nên tập này nhỏ, lấy trọn rẻ hơn lọc theo trang.
        Map<UUID, String> departmentNames = departmentRepository
            .findByTenantIdOrderByNameAsc(tenantId).stream()
            .collect(Collectors.toMap(Department::getId, Department::getName));

        return page.map(position -> PositionResponse.fromEntity(
            position, departmentNames.get(position.getDepartmentId())));
    }

    @Transactional(readOnly = true)
    public PositionResponse getPositionById(UUID id) {
        Position position = getOwnedPosition(id);
        return PositionResponse.fromEntity(position, departmentNameOf(position));
    }

    @Transactional
    public PositionResponse createPosition(CreatePositionRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        String name = request.getName().trim();

        // BR-ORG-13: unique trong phạm vi Tenant.
        if (positionRepository.existsByTenantIdAndName(tenantId, name)) {
            throw new BusinessException("Chức danh đã tồn tại trong khách sạn của bạn: " + name);
        }

        // BR-ORG-07: Department phải có thật và thuộc đúng Tenant này.
        Department department = departmentRepository
            .findByIdAndTenantId(request.getDepartmentId(), tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Department", "id", request.getDepartmentId()));

        Position position = positionRepository.save(Position.builder()
            .tenantId(tenantId)
            .departmentId(department.getId())
            .name(name)
            .positionType(request.getPositionType())
            .build());

        log.info("Tạo Chức danh {} loại {} thuộc Phòng ban {}",
            name, position.getPositionType(), department.getName());
        return PositionResponse.fromEntity(position, department.getName());
    }

    /**
     * Đổi tên và Loại chức danh.
     *
     * <p>Loại giờ chỉ là gợi ý tick sẵn quyền khi Manager chọn chức danh này cho nhân viên MỚI;
     * quyền của người đang giữ nằm ở từng hồ sơ (Manager tick), nên đổi Loại không âm thầm cấp
     * hay thu quyền của ai.
     */
    @Transactional
    public PositionResponse updatePosition(UUID id, UpdatePositionRequest request) {
        Position position = getOwnedPosition(id);
        String name = request.getName().trim();

        if (positionRepository.existsByTenantIdAndNameAndIdNot(
                position.getTenantId(), name, position.getId())) {
            throw new BusinessException("Chức danh đã tồn tại trong khách sạn của bạn: " + name);
        }

        boolean changingType = request.getPositionType() != null
            && request.getPositionType() != position.getPositionType();

        position.setName(name);
        if (changingType) {
            position.setPositionType(request.getPositionType());
        }

        return PositionResponse.fromEntity(positionRepository.save(position), departmentNameOf(position));
    }

    /** BR-ORG-14: ẩn khỏi danh sách chọn thay cho việc xóa. */
    @Transactional
    public PositionResponse setActive(UUID id, boolean active) {
        Position position = getOwnedPosition(id);
        position.setActive(active);

        log.info("{} Chức danh {}", active ? "Hiện" : "Ẩn", position.getId());
        return PositionResponse.fromEntity(positionRepository.save(position), departmentNameOf(position));
    }

    /**
     * BR-ORG-10: chặn cứng nếu đang có nhân viên gắn với chức danh này — làm vị trí chính hoặc
     * kiêm nhiệm — TÍNH CẢ người đã nghỉ việc: hồ sơ của họ vẫn trỏ vào Position để tra lịch sử
     * (BR-USER-04).
     */
    @Transactional
    public void deletePosition(UUID id) {
        Position position = getOwnedPosition(id);

        if (userRepository.isPositionHeld(position.getId())) {
            throw new BusinessException(
                "Không xóa được Chức danh: vẫn còn nhân viên được gán (tính cả người đã nghỉ việc). "
                + "Hãy ẩn chức danh thay vì xóa.");
        }

        positionRepository.delete(position);
        log.info("Xóa Chức danh {}", position.getId());
    }

    private Position getOwnedPosition(UUID id) {
        return positionRepository.findByIdAndTenantId(id, SecurityUtils.getCurrentTenantId())
            .orElseThrow(() -> new ResourceNotFoundException("Position", "id", id));
    }

    private String departmentNameOf(Position position) {
        return departmentRepository
            .findByIdAndTenantId(position.getDepartmentId(), position.getTenantId())
            .map(Department::getName)
            .orElse(null);
    }
}
