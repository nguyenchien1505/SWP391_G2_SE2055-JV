package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.RoomStatusHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
