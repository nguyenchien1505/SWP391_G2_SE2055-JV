package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.Room;
import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Phòng — dùng chung cho module Tổ chức (BR-ORG-04, BR-ORG-05, BR-ORG-11) và module
 * Quản lý phòng (BR-ROOM-*).
 *
 * <p>Mọi truy vấn của module phòng đều nhận {@code tenantId} để cách ly dữ liệu và bỏ qua
 * phòng đã xóa mềm ({@code active = false}, BR-ROOM-08) — phòng đã xóa coi như không tồn tại.
 */
@Repository
public interface RoomRepository extends JpaRepository<Room, UUID> {

    // ── Module Tổ chức ──────────────────────────────────────────────────────

    /** BR-ORG-04: "Tổng số phòng" là derived field — đếm phòng thực tế, không nhập tay. */
    long countByLocationIdAndActiveTrue(UUID locationId);

    /** BR-ORG-05: phòng đã xóa mềm KHÔNG tính khi xét điều kiện xóa Location. */
    boolean existsByLocationIdAndActiveTrue(UUID locationId);

    /**
     * Xóa Loại phòng bị chặn khi còn phòng trỏ vào — tính cả phòng đã xóa mềm, vì bản ghi
     * vẫn giữ khóa ngoại nên DB chặn xóa (BR-ORG-14 là lý do có cờ is_active thay thế).
     */
    boolean existsByRoomTypeId(UUID roomTypeId);

    /**
     * Đếm gộp cho màn hình danh sách Location — tránh N+1 query khi mỗi Location đều cần
     * "Tổng số phòng" (BR-ORG-04). Chỉ đếm cho các Location của TRANG hiện tại.
     *
     * <p>Location chưa có phòng nào sẽ không xuất hiện trong kết quả; bên gọi tự quy về 0.
     * Không gọi với danh sách rỗng — xem {@code LocationService#getLocations}.
     */
    @Query("""
        SELECT r.locationId AS locationId, COUNT(r) AS total
        FROM Room r
        WHERE r.tenantId = :tenantId AND r.active = true AND r.locationId IN :locationIds
        GROUP BY r.locationId
        """)
    List<LocationRoomCount> countActiveRoomsGroupedByLocation(@Param("tenantId") UUID tenantId,
                                                              @Param("locationIds") Collection<UUID> locationIds);

    interface LocationRoomCount {
        UUID getLocationId();
        long getTotal();
    }

    // ── Module Quản lý phòng ────────────────────────────────────────────────

    /**
     * Chốt chặn cách ly Tenant cho mọi thao tác theo id: phòng của Tenant khác hoặc đã xóa
     * mềm đều trả rỗng, service biến thành 404 — không lộ việc phòng đó có tồn tại.
     */
    Optional<Room> findByIdAndTenantIdAndActiveTrue(UUID id, UUID tenantId);

    /**
     * BR-ROOM-05: số phòng duy nhất trong phạm vi LOCATION, không phải toàn hệ thống. Phòng đã
     * xóa mềm KHÔNG chiếm chỗ — số phòng cũ được dùng lại, đúng như ràng buộc UNIQUE ở DB
     * ({@code uk_rooms_location_active_number} trên cột sinh {@code active_room_number}).
     */
    boolean existsByLocationIdAndRoomNumberAndActiveTrue(UUID locationId, String roomNumber);

    /**
     * Bản dùng khi SỬA phòng: bỏ chính phòng đang sửa ra khỏi phép kiểm tra, nếu không thì lưu
     * lại mà không đổi số phòng cũng bị báo trùng với chính nó.
     */
    boolean existsByLocationIdAndRoomNumberAndActiveTrueAndIdNot(UUID locationId, String roomNumber,
                                                                UUID id);

    /**
     * Danh sách phòng có lọc — S-02, S-06 (BR-ROOM-06: lọc ở server để màn hình điện thoại
     * không phải tải toàn bộ).
     *
     * <p>Tham số nào {@code null} thì bỏ qua điều kiện đó, riêng {@code tenantId} luôn bắt
     * buộc. {@code locationId} do service quyết định theo vai trò: Giám đốc được để trống
     * (xem toàn Tenant), Manager/Staff luôn bị ép về Location của mình.
     */
    @Query("""
        SELECT r FROM Room r
        WHERE r.tenantId = :tenantId AND r.active = true
          AND (:locationId IS NULL OR r.locationId = :locationId)
          AND (:status     IS NULL OR r.status     = :status)
          AND (:floor      IS NULL OR r.floor      = :floor)
          AND (:roomTypeId IS NULL OR r.roomTypeId = :roomTypeId)
        """)
    Page<Room> search(@Param("tenantId") UUID tenantId,
                      @Param("locationId") UUID locationId,
                      @Param("status") RoomStatus status,
                      @Param("floor") String floor,
                      @Param("roomTypeId") UUID roomTypeId,
                      Pageable pageable);

    /**
     * Đếm phòng theo trạng thái — BR-DASH-02 ({@code locationId = null}: toàn Tenant),
     * BR-DASH-03 (một Location), và dải thẻ số liệu đầu trang S-02/S-06.
     *
     * <p>Trạng thái không có phòng nào sẽ KHÔNG có dòng trong kết quả (GROUP BY chỉ trả
     * nhóm tồn tại); service tự bù về 0 cho đủ 7 trạng thái.
     */
    @Query("""
        SELECT r.status AS status, COUNT(r) AS total
        FROM Room r
        WHERE r.tenantId = :tenantId AND r.active = true
          AND (:locationId IS NULL OR r.locationId = :locationId)
        GROUP BY r.status
        """)
    List<RoomStatusCount> countByStatus(@Param("tenantId") UUID tenantId,
                                        @Param("locationId") UUID locationId);

    interface RoomStatusCount {
        RoomStatus getStatus();
        long getTotal();
    }
}
