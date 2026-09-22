package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.RoomTypeRequest;
import com.example.SWP391_G2_SE2055_JV.dto.RoomTypeResponse;
import com.example.SWP391_G2_SE2055_JV.entity.RoomType;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.RoomRepository;
import com.example.SWP391_G2_SE2055_JV.repository.RoomTypeRepository;
import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Loại phòng — BR-ORG-11, BR-ORG-13, BR-ORG-14.
 *
 * <p>Danh mục cấp TENANT, Giám đốc định nghĩa và dùng chung cho mọi Location: hai khách
 * sạn cùng chuỗi hiểu "Deluxe" giống nhau thì báo cáo tổng hợp mới cộng được.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomTypeService {

    private final RoomTypeRepository roomTypeRepository;
    private final RoomRepository     roomRepository;

    @Transactional(readOnly = true)
    public List<RoomTypeResponse> getRoomTypes(boolean includeInactive) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        List<RoomType> roomTypes = includeInactive
            ? roomTypeRepository.findByTenantIdOrderByNameAsc(tenantId)
            : roomTypeRepository.findByTenantIdAndActiveTrueOrderByNameAsc(tenantId);

        return roomTypes.stream().map(RoomTypeResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public RoomTypeResponse getRoomTypeById(UUID id) {
        return RoomTypeResponse.fromEntity(getOwnedRoomType(id));
    }

    @Transactional
    public RoomTypeResponse createRoomType(RoomTypeRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        String name = request.getName().trim();

        // BR-ORG-13: unique trong phạm vi Tenant.
        if (roomTypeRepository.existsByTenantIdAndName(tenantId, name)) {
            throw new BusinessException("Loại phòng đã tồn tại trong khách sạn của bạn: " + name);
        }

        RoomType roomType = roomTypeRepository.save(
            RoomType.builder().tenantId(tenantId).name(name).build());

        log.info("Tạo Loại phòng {} cho Tenant {}", name, tenantId);
        return RoomTypeResponse.fromEntity(roomType);
    }

    @Transactional
    public RoomTypeResponse updateRoomType(UUID id, RoomTypeRequest request) {
        RoomType roomType = getOwnedRoomType(id);
        String name = request.getName().trim();

        if (roomTypeRepository.existsByTenantIdAndNameAndIdNot(
                roomType.getTenantId(), name, roomType.getId())) {
            throw new BusinessException("Loại phòng đã tồn tại trong khách sạn của bạn: " + name);
        }

        roomType.setName(name);
        return RoomTypeResponse.fromEntity(roomTypeRepository.save(roomType));
    }

    /** BR-ORG-14: ẩn khỏi danh sách chọn thay cho việc xóa. */
    @Transactional
    public RoomTypeResponse setActive(UUID id, boolean active) {
        RoomType roomType = getOwnedRoomType(id);
        roomType.setActive(active);

        log.info("{} Loại phòng {}", active ? "Hiện" : "Ẩn", roomType.getId());
        return RoomTypeResponse.fromEntity(roomTypeRepository.save(roomType));
    }

    /**
     * Chặn xóa khi còn phòng trỏ vào — TÍNH CẢ phòng đã xóa mềm, vì bản ghi phòng vẫn giữ
     * khóa ngoại tới loại phòng để tra lịch sử (BR-ROOM-08). Đây chính là lý do BR-ORG-14
     * cho danh mục một cờ is_active để ẩn thay vì xóa.
     */
    @Transactional
    public void deleteRoomType(UUID id) {
        RoomType roomType = getOwnedRoomType(id);

        if (roomRepository.existsByRoomTypeId(roomType.getId())) {
            throw new BusinessException(
                "Không xóa được Loại phòng: vẫn còn phòng thuộc loại này. "
                + "Hãy ẩn loại phòng thay vì xóa (BR-ORG-14).");
        }

        roomTypeRepository.delete(roomType);
        log.info("Xóa Loại phòng {}", roomType.getId());
    }

    private RoomType getOwnedRoomType(UUID id) {
        return roomTypeRepository.findByIdAndTenantId(id, SecurityUtils.getCurrentTenantId())
            .orElseThrow(() -> new ResourceNotFoundException("RoomType", "id", id));
    }
}
