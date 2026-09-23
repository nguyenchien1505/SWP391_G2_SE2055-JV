package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.FixedAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Chỉ chứa truy vấn mà module Tổ chức cần (BR-ORG-15); nghiệp vụ tài sản đầy đủ thuộc
 * module BR-ASSET.
 */
@Repository
public interface FixedAssetRepository extends JpaRepository<FixedAsset, UUID> {

    /**
     * BR-ORG-15: chặn xóa Khu vực khi còn tài sản cố định gắn vào. Tính cả tài sản đã
     * thanh lý — bản ghi vẫn giữ khóa ngoại tới khu vực để tra lịch sử (BR-ASSET-14).
     */
    boolean existsByAreaId(UUID areaId);

    /**
     * BR-ROOM-08: chặn xóa Phòng khi còn tài sản cố định gắn vào. Tính cả tài sản đã thanh lý —
     * cùng quy ước với {@link #existsByAreaId} (BR-ASSET-14 giữ khóa ngoại để tra lịch sử).
     */
    boolean existsByRoomId(UUID roomId);
}
