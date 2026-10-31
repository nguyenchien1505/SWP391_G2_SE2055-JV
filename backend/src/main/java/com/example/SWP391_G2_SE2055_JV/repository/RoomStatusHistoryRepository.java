package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.RoomStatusHistory;
import com.example.SWP391_G2_SE2055_JV.enums.ChangeSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Nhật ký đổi trạng thái phòng — BR-ROOM-09.
 *
 * <p>Bảng append-only: chỉ {@code RoomStatusService} được ghi (mỗi bước chuyển đúng 1 dòng,
 * cùng transaction với việc đổi {@code rooms.status}); không ai sửa hay xóa dòng đã ghi.
 */
@Repository
public interface RoomStatusHistoryRepository extends JpaRepository<RoomStatusHistory, UUID> {

    /**
     * S-05 Lịch sử trạng thái — mới nhất trước. Service đã kiểm tra phòng thuộc phạm vi người
     * xem (404 nếu không); {@code tenantId} vẫn có mặt để truy vấn nào cũng mang chốt cách ly
     * Tenant, theo quy ước của codebase.
     */
    Page<RoomStatusHistory> findByTenantIdAndRoomIdOrderByChangedAtDesc(UUID tenantId, UUID roomId,
                                                                        Pageable pageable);

    /**
     * Mọi lần đổi trạng thái của một nguồn ({@code RECEPTION}…) ở một khách sạn trong [from, to], cũ nhất trước —
     * chấm công M2 đối chiếu thao tác của lễ tân với giờ vào / ra ca, không cần thêm cột nào vào bảng này.
     */
    @Query("""
        SELECT h FROM RoomStatusHistory h, Room r
        WHERE r.id = h.roomId
          AND h.tenantId = :tenantId
          AND r.locationId = :locationId
          AND h.changeSource = :source
          AND h.changedAt BETWEEN :from AND :to
        ORDER BY h.changedAt
        """)
    List<RoomStatusHistory> findBySourceAtLocationBetween(@Param("tenantId") UUID tenantId,
                                                          @Param("locationId") UUID locationId,
                                                          @Param("source") ChangeSource source,
                                                          @Param("from") LocalDateTime from,
                                                          @Param("to") LocalDateTime to);
}
