package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Chứa các truy vấn mà module Tổ chức cần (BR-ORG-04, BR-ORG-05, BR-ORG-11)
 * và tra cứu Phòng phục vụ việc gắn tài sản cố định — BR-ASSET-03.

 */
@Repository
public interface RoomRepository extends JpaRepository<Room, UUID> {

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

    Optional<Room> findByIdAndTenantId(UUID id, UUID tenantId);
}
