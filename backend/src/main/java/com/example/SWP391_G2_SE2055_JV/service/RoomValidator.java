package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.entity.Room;
import com.example.SWP391_G2_SE2055_JV.entity.RoomType;
import com.example.SWP391_G2_SE2055_JV.enums.HousekeepingTaskStatus;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.FixedAssetRepository;
import com.example.SWP391_G2_SE2055_JV.repository.HousekeepingTaskRepository;
import com.example.SWP391_G2_SE2055_JV.repository.LocationRepository;
import com.example.SWP391_G2_SE2055_JV.repository.RoomRepository;
import com.example.SWP391_G2_SE2055_JV.repository.RoomTypeRepository;
import com.example.SWP391_G2_SE2055_JV.repository.SubscriptionRepository;
import com.example.SWP391_G2_SE2055_JV.repository.TenantUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Điều kiện được phép TẠO / SỬA / XÓA một phòng — BR-ROOM-05, BR-ROOM-08, BR-ORG-14,
 * BR-SAAS-02.
 *
 * <p>Tách khỏi {@link RoomService} theo cùng khuôn với {@link SchedulePolicyValidator}: những
 * luật này phải hỏi 5 module khác (Tổ chức, Loại phòng, Housekeeping, Tài sản, SaaS), trong khi
 * {@code RoomService} chỉ lo điều phối và phạm vi dữ liệu. Để chung một lớp thì lớp đó vừa điều
 * phối vừa nắm luật của 5 module — sửa luật quota cũng phải mở file phục vụ mọi endpoint phòng.
 *
 * <p>Toàn bộ là hàm kiểm tra thuần: hợp lệ thì trả về (hoặc trả entity đã tra), sai thì ném lỗi
 * — 404 cho dữ liệu ngoài Tenant (không lộ sự tồn tại), 400 cho vi phạm nghiệp vụ. Lớp này KHÔNG
 * ghi gì vào DB và không đọc người đang đăng nhập: {@code tenantId} do bên gọi truyền vào.
 */
@Component
@RequiredArgsConstructor
public class RoomValidator {

    private final RoomRepository             roomRepository;
    private final LocationRepository         locationRepository;
    private final RoomTypeRepository         roomTypeRepository;
    private final SubscriptionRepository     subscriptionRepository;
    private final TenantUsageRepository      tenantUsageRepository;
    private final HousekeepingTaskRepository taskRepository;
    private final FixedAssetRepository       fixedAssetRepository;

    /**
     * Khách sạn nhận phòng mới phải thuộc Tenant của người tạo. Khóa ngoại chỉ đảm bảo Location
     * TỒN TẠI, không đảm bảo cùng Tenant — thiếu chốt này thì Giám đốc chuỗi A tạo được phòng
     * trong chuỗi B. Khác Tenant trả 404 chứ không phải 403.
     */
    public void assertLocationInTenant(UUID locationId, UUID tenantId) {
        if (locationRepository.findByIdAndTenantId(locationId, tenantId).isEmpty()) {
            throw new ResourceNotFoundException("Location", "id", locationId);
        }
    }

    /** Loại phòng thuộc Tenant, kể cả loại đã ẩn — dùng khi phòng GIỮ NGUYÊN loại cũ (BR-ORG-14). */
    public RoomType requireRoomType(UUID roomTypeId, UUID tenantId) {
        return roomTypeRepository.findByIdAndTenantId(roomTypeId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("RoomType", "id", roomTypeId));
    }

    /**
     * BR-ORG-14: loại phòng đã ẩn chỉ còn giá trị lịch sử cho phòng cũ, không được CHỌN MỚI.
     * Dùng khi tạo phòng và khi đổi sang một loại khác.
     */
    public RoomType requireActiveRoomType(UUID roomTypeId, UUID tenantId) {
        RoomType roomType = requireRoomType(roomTypeId, tenantId);
        if (!roomType.isActive()) {
            throw new BusinessException("Loại phòng «" + roomType.getName()
                + "» đã ngừng sử dụng nên không chọn được. Hãy chọn loại phòng khác.");
        }
        return roomType;
    }

    /**
     * BR-ROOM-05: số phòng duy nhất trong phạm vi khách sạn. Phòng đã xóa mềm không tính, nên
     * số phòng cũ dùng lại được.
     *
     * @param excludeRoomId phòng đang sửa, bỏ ra khỏi phép kiểm tra; {@code null} khi tạo mới
     */
    public void assertRoomNumberFree(UUID locationId, String roomNumber, UUID excludeRoomId) {
        boolean taken = excludeRoomId == null
            ? roomRepository.existsByLocationIdAndRoomNumberAndActiveTrue(locationId, roomNumber)
            : roomRepository.existsByLocationIdAndRoomNumberAndActiveTrueAndIdNot(
                locationId, roomNumber, excludeRoomId);

        if (taken) {
            throw new BusinessException("Khách sạn này đã có phòng số " + roomNumber
                + ". Mỗi khách sạn không được có hai phòng trùng số.");
        }
    }

    /**
     * BR-SAAS-02: không tạo quá hạn mức phòng của gói dịch vụ.
     *
     * <p>Đếm bằng đúng {@link TenantUsageRepository#countRooms} — hàm này tính CẢ phòng đã xóa
     * mềm, theo quy ước đếm mà team đã chốt. Hệ quả: xóa phòng KHÔNG trả lại hạn mức, màn hình
     * tạo phòng phải nói rõ điều đó.
     *
     * <p>Tenant chưa có Subscription thì không chặn — cùng cách xử lý với
     * {@link TenantUsageService}: thiếu gói dịch vụ là dữ liệu chưa đủ, không phải vi phạm.
     */
    public void assertRoomQuotaAvailable(UUID tenantId) {
        subscriptionRepository.findByTenantId(tenantId).ifPresent(subscription -> {
            if (tenantUsageRepository.countRooms(tenantId) >= subscription.getQuotaRoom()) {
                throw new BusinessException("Đã dùng hết hạn mức " + subscription.getQuotaRoom()
                    + " phòng của gói dịch vụ. Hãy nâng gói trước khi tạo thêm phòng.");
            }
        });
    }

    /**
     * BR-ROOM-08: ba điều kiện xóa phòng, kiểm theo thứ tự rẻ tiền trước — trạng thái (đã có
     * sẵn trên entity), rồi task dọn đang mở, rồi tài sản cố định.
     *
     * <p>Câu lỗi nêu đúng điều kiện đang vi phạm để Giám đốc biết phải làm gì trước.
     */
    public void assertDeletable(Room room) {
        if (!room.isDeletable()) {
            throw new BusinessException("Phòng " + room.getRoomNumber() + " đang ở trạng thái «"
                + room.getStatus().label() + "» nên chưa xóa được. Chỉ xóa được phòng đang "
                + "«Trống / Sẵn sàng» hoặc «Không khả dụng».");
        }
        if (!taskRepository.findByRoomIdAndStatusIn(room.getId(), HousekeepingTaskStatus.OPEN_STATUSES)
                .isEmpty()) {
            throw new BusinessException("Phòng " + room.getRoomNumber()
                + " còn việc dọn phòng chưa kết thúc. Hãy hoàn thành hoặc hủy việc dọn trước khi xóa.");
        }
        if (fixedAssetRepository.existsByRoomId(room.getId())) {
            throw new BusinessException("Phòng " + room.getRoomNumber()
                + " còn tài sản cố định gắn vào. Hãy chuyển tài sản sang nơi khác hoặc thanh lý trước khi xóa.");
        }
    }
}
