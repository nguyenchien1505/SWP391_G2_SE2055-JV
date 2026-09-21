package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Tra cứu Phòng phục vụ việc gắn tài sản cố định — BR-ASSET-03.
 *
 * <p>Nghiệp vụ quản lý phòng (BR-ROOM) KHÔNG nằm ở đây; repository này cố ý chỉ có
 * đúng một method đọc. Khóa ngoại {@code fk_fixed_assets_room} chỉ đảm bảo phòng TỒN
 * TẠI, không đảm bảo nó thuộc cùng Tenant/Location với người thao tác — nên mọi
 * {@code roomId} nhận từ client đều phải đi qua đây trước khi lưu.
 */
@Repository
public interface RoomRepository extends JpaRepository<Room, UUID> {

    Optional<Room> findByIdAndTenantId(UUID id, UUID tenantId);
}
