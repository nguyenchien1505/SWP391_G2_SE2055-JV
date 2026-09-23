package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.ChangeRoomStatusRequest;
import com.example.SWP391_G2_SE2055_JV.dto.CreateRoomRequest;
import com.example.SWP391_G2_SE2055_JV.dto.RoomResponse;
import com.example.SWP391_G2_SE2055_JV.dto.RoomStatusHistoryResponse;
import com.example.SWP391_G2_SE2055_JV.dto.RoomStatusSummaryResponse;
import com.example.SWP391_G2_SE2055_JV.dto.UpdateRoomOperationalRequest;
import com.example.SWP391_G2_SE2055_JV.dto.UpdateRoomRequest;
import com.example.SWP391_G2_SE2055_JV.entity.Room;
import com.example.SWP391_G2_SE2055_JV.entity.RoomStatusHistory;
import com.example.SWP391_G2_SE2055_JV.entity.RoomType;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.RoomRepository;
import com.example.SWP391_G2_SE2055_JV.repository.RoomStatusHistoryRepository;
import com.example.SWP391_G2_SE2055_JV.repository.RoomTypeRepository;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Phòng — mặt tiền của mọi endpoint {@code /rooms/**} (BR-ROOM-*).
 *
 * <ul>
 *   <li>F1 — ĐỌC: danh sách, chi tiết, đếm theo trạng thái (RM-06).</li>
 *   <li>F2 — đổi trạng thái (RM-11, RM-12; ủy quyền cho {@link RoomStatusService}) và xem lịch sử
 *       trạng thái (RM-07). Mỗi phòng trả về kèm {@code allowedTargets} do
 *       {@link RoomTransitionPolicy} tính.</li>
 *   <li>F3 — Giám đốc tạo/sửa/xóa phòng (RM-02, RM-03, RM-05) và Manager sửa ghi chú vận
 *       hành (RM-04). Điều kiện được phép ghi nằm ở {@link RoomValidator}.</li>
 * </ul>
 *
 * <p>Lớp này KHÔNG tự đổi trạng thái phòng: nó chỉ tải phòng đúng phạm vi rồi giao cho
 * {@link RoomStatusService} — nơi duy nhất ghi {@code rooms.status}.
 *
 * <p><b>Phạm vi dữ liệu</b> (lớp kiểm tra thứ 3, sau URL và {@code @PreAuthorize}):
 * <ul>
 *   <li>Mọi truy vấn lọc theo Tenant của người đang đăng nhập — không có {@code findAll()} trần.</li>
 *   <li>Giám đốc thấy toàn Tenant; Manager và Staff (Lễ tân, Dọn dẹp) bị ÉP về Location của
 *       mình, bất kể client gửi {@code locationId} nào.</li>
 *   <li>Phòng ngoài phạm vi trả 404 chứ không phải 403, để không lộ việc phòng đó tồn tại.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository              roomRepository;
    private final RoomTypeRepository          roomTypeRepository;
    private final RoomStatusHistoryRepository historyRepository;
    private final UserRepository              userRepository;
    private final RoomStatusService           roomStatusService;
    private final RoomTransitionPolicy        transitionPolicy;
    private final RoomValidator               validator;

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
        return page.map(room -> toResponse(room, roomTypeNames.get(room.getRoomTypeId())));
    }

    /** S-04 Chi tiết phòng. */
    @Transactional(readOnly = true)
    public RoomResponse getRoomById(UUID id) {
        return toResponse(getOwnedRoom(id));
    }

    /**
     * {@code PATCH /rooms/{id}/status} — khóa / mở khóa phòng (RM-11, RM-12) và các bước của Lễ
     * tân. Phòng ngoài phạm vi → 404 TRƯỚC mọi kiểm tra khác; phần còn lại (ma trận, quyền, lý
     * do, hệ quả lên task) do {@link RoomStatusService#changeStatusByUser} làm.
     *
     * @return phòng sau khi đổi, kèm {@code allowedTargets} MỚI để màn hình vẽ lại nút ngay
     */
    @Transactional
    public RoomResponse changeStatus(UUID id, ChangeRoomStatusRequest request) {
        Room room = getOwnedRoom(id);
        roomStatusService.changeStatusByUser(room, request);
        return toResponse(room);
    }

    /**
     * S-05 Lịch sử trạng thái — BR-ROOM-09. Cùng quyền đọc với chi tiết phòng (cả 4 role, trong
     * phạm vi). Luôn mới nhất trước: bỏ qua tham số {@code sort} client gửi, chỉ giữ trang và cỡ.
     */
    @Transactional(readOnly = true)
    public Page<RoomStatusHistoryResponse> getHistory(UUID id, Pageable pageable) {
        Room room = getOwnedRoom(id);
        Page<RoomStatusHistory> page = historyRepository.findByTenantIdAndRoomIdOrderByChangedAtDesc(
            room.getTenantId(), room.getId(),
            PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));

        Map<UUID, String> names = userNamesOf(page.getContent());
        return page.map(row -> RoomStatusHistoryResponse.fromEntity(
            row, row.getChangedBy() == null ? null : names.get(row.getChangedBy())));
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

    // ── F3: tạo / sửa / xóa phòng ───────────────────────────────────────────

    /**
     * RM-02 Giám đốc tạo phòng — BR-ROOM-04, BR-ROOM-05, BR-ROOM-10.
     *
     * <p>Thứ tự kiểm tra cố định để câu lỗi luôn nhất quán: khách sạn (404) → loại phòng (404,
     * hoặc 400 nếu loại đã ẩn) → số phòng trùng (400) → hạn mức gói dịch vụ (400). Kiểm tra phạm
     * vi dữ liệu đi TRƯỚC kiểm tra nghiệp vụ, để người của Tenant khác không dò được dữ liệu qua
     * nội dung câu lỗi.
     *
     * <p>Phòng mới vào thẳng «Chờ dọn» chứ không phải «Sẵn sàng»:
     * {@link RoomStatusService#recordInitialStatus} ghi dòng lịch sử đầu tiên và sinh việc dọn
     * chưa phân công (BR-HK-01), cùng transaction với lệnh lưu phòng — hỏng bước nào thì không
     * để lại phòng tạo dở.
     */
    @Transactional
    public RoomResponse createRoom(CreateRoomRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID locationId = request.getLocationId();
        String roomNumber = request.getRoomNumber().trim();

        validator.assertLocationInTenant(locationId, tenantId);
        RoomType roomType = validator.requireActiveRoomType(request.getRoomTypeId(), tenantId);
        validator.assertRoomNumberFree(locationId, roomNumber, null);
        validator.assertRoomQuotaAvailable(tenantId);

        Room saved = roomRepository.save(Room.builder()
            .tenantId(tenantId)
            .locationId(locationId)
            .roomNumber(roomNumber)
            .floor(request.getFloor().trim())
            .roomTypeId(roomType.getId())
            .capacity(request.getCapacity())
            .note(normalizeNote(request.getNote()))
            .status(RoomStatus.DIRTY)
            .build());

        roomStatusService.recordInitialStatus(saved);
        log.info("Tạo phòng {} tại Location {}", saved.getRoomNumber(), locationId);
        return toResponse(saved, roomType.getName());
    }

    /**
     * RM-03 Giám đốc sửa thông tin CẤU TRÚC của phòng — BR-ROOM-04, BR-ROOM-05.
     *
     * <p>Không bao giờ đụng {@code status}, {@code unavailableReason} hay {@code locationId}:
     * trạng thái chỉ đổi qua {@link RoomStatusService}, còn chuyển phòng sang khách sạn khác
     * không phải nghiệp vụ có thật nên DTO cũng không nhận trường đó.
     */
    @Transactional
    public RoomResponse updateRoom(UUID id, UpdateRoomRequest request) {
        Room room = getOwnedRoom(id);
        String roomNumber = request.getRoomNumber().trim();

        // Chỉ hỏi DB khi số phòng thật sự đổi — lưu lại y nguyên số cũ không phải là trùng.
        if (!roomNumber.equals(room.getRoomNumber())) {
            validator.assertRoomNumberFree(room.getLocationId(), roomNumber, room.getId());
        }
        RoomType roomType = resolveRoomType(request.getRoomTypeId(), room);

        room.setRoomNumber(roomNumber);
        room.setFloor(request.getFloor().trim());
        room.setRoomTypeId(roomType.getId());
        room.setCapacity(request.getCapacity());
        room.setNote(normalizeNote(request.getNote()));

        log.info("Sửa phòng {} ({})", room.getRoomNumber(), room.getId());
        return toResponse(roomRepository.save(room), roomType.getName());
    }

    /**
     * RM-04 Manager sửa thông tin VẬN HÀNH — BR-ROOM-04. Chỉ ghi chú: số phòng, tầng, loại phòng
     * và sức chứa là thông tin cấu trúc, thuộc quyền Giám đốc.
     *
     * <p>Phạm vi do {@link #getOwnedRoom} lo — Manager chạm vào phòng của khách sạn khác nhận
     * 404. Giám đốc cũng gọi được endpoint này khi chỉ muốn sửa mỗi ghi chú.
     */
    @Transactional
    public RoomResponse updateOperational(UUID id, UpdateRoomOperationalRequest request) {
        Room room = getOwnedRoom(id);
        room.setNote(normalizeNote(request.getNote()));
        return toResponse(roomRepository.save(room));
    }

    /**
     * RM-05 Giám đốc xóa phòng — BR-ROOM-08. Là xóa MỀM: phòng đã có lịch sử trạng thái và có
     * thể còn task cũ trỏ tới, xóa cứng sẽ mất dấu vết.
     *
     * <p>Số phòng được giải phóng ngay nhờ cột sinh {@code active_room_number} nên tạo lại phòng
     * trùng số là được. Hạn mức gói dịch vụ thì KHÔNG được trả lại — xem
     * {@link RoomValidator#assertRoomQuotaAvailable}.
     */
    @Transactional
    public void deleteRoom(UUID id) {
        Room room = getOwnedRoom(id);
        validator.assertDeletable(room);

        room.setActive(false);
        roomRepository.save(room);
        log.info("Xóa mềm phòng {} ({})", room.getRoomNumber(), room.getId());
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
     * (với Manager/Staff) khác Location → 404. Mọi thao tác theo id (đọc, đổi trạng thái, lịch sử)
     * đều đi qua đây trước.
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

    private RoomResponse toResponse(Room room) {
        String roomTypeName = roomTypeRepository.findByIdAndTenantId(room.getRoomTypeId(), room.getTenantId())
            .map(RoomType::getName)
            .orElse(null);
        return toResponse(room, roomTypeName);
    }

    /** Mọi phòng trả ra đều kèm các nút người đang đăng nhập được bấm — một nguồn luật duy nhất. */
    private RoomResponse toResponse(Room room, String roomTypeName) {
        Set<RoomStatus> allowedTargets = transitionPolicy.allowedTargetsForCurrentUser(room.getStatus());
        return RoomResponse.fromEntity(room, roomTypeName, allowedTargets);
    }

    /**
     * id → họ tên của những người xuất hiện trong MỘT trang lịch sử, tra một lần thay vì mỗi dòng
     * một câu truy vấn. Dòng do hệ thống ghi ({@code changedBy = null}) không cần tra.
     */
    private Map<UUID, String> userNamesOf(Collection<RoomStatusHistory> rows) {
        Set<UUID> ids = rows.stream()
            .map(RoomStatusHistory::getChangedBy)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(User::getId, User::getFullName));
    }

    /**
     * id → tên của MỌI loại phòng trong Tenant, kể cả loại đã ẩn (BR-ORG-14): phòng cũ vẫn
     * trỏ tới loại đã ẩn và vẫn phải hiện đúng tên. Danh mục cấp Tenant nên số lượng nhỏ.
     */
    private Map<UUID, String> roomTypeNamesOf(UUID tenantId) {
        return roomTypeRepository.findByTenantIdOrderByNameAsc(tenantId).stream()
            .collect(Collectors.toMap(RoomType::getId, RoomType::getName));
    }

    /**
     * Loại phòng hợp lệ cho phòng SAU KHI sửa — BR-ORG-14. Giữ nguyên loại cũ thì vẫn lưu được
     * dù loại đó đã bị ẩn (không ép Giám đốc đổi loại chỉ vì muốn sửa ghi chú); ĐỔI sang loại
     * khác thì loại mới bắt buộc còn đang dùng.
     */
    private RoomType resolveRoomType(UUID requestedRoomTypeId, Room room) {
        return requestedRoomTypeId.equals(room.getRoomTypeId())
            ? validator.requireRoomType(requestedRoomTypeId, room.getTenantId())
            : validator.requireActiveRoomType(requestedRoomTypeId, room.getTenantId());
    }

    /** Ô ghi chú để trống hoặc toàn khoảng trắng lưu thành NULL, không phải chuỗi rỗng. */
    private static String normalizeNote(String note) {
        return StringUtils.hasText(note) ? note.trim() : null;
    }

    /** Tầng là text (G, M, B1 — BR-ROOM-05); ô lọc để trống hoặc toàn khoảng trắng = không lọc. */
    private static String normalizeFloor(String floor) {
        return StringUtils.hasText(floor) ? floor.trim() : null;
    }
}
