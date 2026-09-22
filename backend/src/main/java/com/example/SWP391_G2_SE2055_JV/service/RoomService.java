package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.RoomResponse;
import com.example.SWP391_G2_SE2055_JV.dto.RoomStatusSummaryResponse;
import com.example.SWP391_G2_SE2055_JV.entity.Room;
import com.example.SWP391_G2_SE2055_JV.entity.RoomType;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.RoomRepository;
import com.example.SWP391_G2_SE2055_JV.repository.RoomTypeRepository;
import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Phòng — mặt tiền của mọi endpoint {@code /rooms/**} (BR-ROOM-*).
 *
 * <p>F1 chỉ có phần ĐỌC: danh sách, chi tiết, đếm theo trạng thái (RM-06). Các tính năng sau
 * bổ sung vào đây: đổi trạng thái + lịch sử (F2, ủy quyền cho {@code RoomStatusService}),
 * tạo/sửa/xóa phòng (F3).
 *
 * <p><b>Phạm vi dữ liệu</b> (lớp kiểm tra thứ 3, sau URL và {@code @PreAuthorize}):
 * <ul>
 *   <li>Mọi truy vấn lọc theo Tenant của người đang đăng nhập — không có {@code findAll()} trần.</li>
 *   <li>Giám đốc thấy toàn Tenant; Manager và Staff (Lễ tân, Dọn dẹp) bị ÉP về Location của
 *       mình, bất kể client gửi {@code locationId} nào.</li>
 *   <li>Phòng ngoài phạm vi trả 404 chứ không phải 403, để không lộ việc phòng đó tồn tại.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository     roomRepository;
    private final RoomTypeRepository roomTypeRepository;

    /**
     * S-02 Danh sách phòng, S-06 Sơ đồ phòng. Mọi bộ lọc đều tùy chọn và chạy ở server
     * (BR-ROOM-06).
     *
     * @param locationId chỉ có tác dụng với Giám đốc; vai trò khác bị ghi đè bằng Location của mình
     */
    @Transactional(readOnly = true)
    public Page<RoomResponse> getRooms(UUID locationId, RoomStatus status, String floor,
                                       UUID roomTypeId, Pageable pageable) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Page<Room> page = roomRepository.search(
            tenantId, scopeLocation(locationId), status, normalizeFloor(floor), roomTypeId, pageable);

        // Tra tên loại phòng MỘT lần cho cả trang thay vì mỗi phòng một câu truy vấn.
        Map<UUID, String> roomTypeNames = roomTypeNamesOf(tenantId);
        return page.map(room -> RoomResponse.fromEntity(room, roomTypeNames.get(room.getRoomTypeId())));
    }

    /** S-04 Chi tiết phòng. */
    @Transactional(readOnly = true)
    public RoomResponse getRoomById(UUID id) {
        Room room = getOwnedRoom(id);
        String roomTypeName = roomTypeRepository.findByIdAndTenantId(room.getRoomTypeId(), room.getTenantId())
            .map(RoomType::getName)
            .orElse(null);
        return RoomResponse.fromEntity(room, roomTypeName);
    }

    /**
     * Đếm phòng theo 7 trạng thái — BR-DASH-02 (Giám đốc để trống {@code locationId}: toàn
     * Tenant), BR-DASH-03 (một Location). Cùng quy tắc phạm vi với {@link #getRooms}.
     */
    @Transactional(readOnly = true)
    public RoomStatusSummaryResponse getStatusSummary(UUID locationId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID scopedLocationId = scopeLocation(locationId);
        return RoomStatusSummaryResponse.of(
            scopedLocationId, roomRepository.countByStatus(tenantId, scopedLocationId));
    }

    // ── Phạm vi dữ liệu ─────────────────────────────────────────────────────

    /**
     * Location được phép xem. Giám đốc: đúng giá trị client gửi ({@code null} = toàn Tenant).
     * Manager/Staff: luôn là Location của chính họ — client gửi Location khác cũng bị bỏ qua,
     * nên không có cách nào đọc chéo Location qua tham số.
     */
    private UUID scopeLocation(UUID requestedLocationId) {
        return SecurityUtils.hasRole(Role.DIRECTOR)
            ? requestedLocationId
            : SecurityUtils.getCurrentLocationId();
    }

    /**
     * Tải phòng theo id trong phạm vi của người đang đăng nhập. Khác Tenant, đã xóa mềm, hoặc
     * (với Manager/Staff) khác Location → 404. F2, F3 dùng lại hàm này cho mọi thao tác ghi.
     */
    private Room getOwnedRoom(UUID id) {
        Room room = roomRepository.findByIdAndTenantIdAndActiveTrue(id, SecurityUtils.getCurrentTenantId())
            .orElseThrow(() -> new ResourceNotFoundException("Room", "id", id));

        if (!SecurityUtils.hasRole(Role.DIRECTOR)
                && !room.getLocationId().equals(SecurityUtils.getCurrentLocationId())) {
            throw new ResourceNotFoundException("Room", "id", id);
        }
        return room;
    }

    // ── Tiện ích nội bộ ─────────────────────────────────────────────────────

    /**
     * id → tên của MỌI loại phòng trong Tenant, kể cả loại đã ẩn (BR-ORG-14): phòng cũ vẫn
     * trỏ tới loại đã ẩn và vẫn phải hiện đúng tên. Danh mục cấp Tenant nên số lượng nhỏ.
     */
    private Map<UUID, String> roomTypeNamesOf(UUID tenantId) {
        return roomTypeRepository.findByTenantIdOrderByNameAsc(tenantId).stream()
            .collect(Collectors.toMap(RoomType::getId, RoomType::getName));
    }

    /** Tầng là text (G, M, B1 — BR-ROOM-05); ô lọc để trống hoặc toàn khoảng trắng = không lọc. */
    private static String normalizeFloor(String floor) {
        return StringUtils.hasText(floor) ? floor.trim() : null;
    }
}
