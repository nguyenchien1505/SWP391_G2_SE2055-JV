package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.InspectionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Biên bản kiểm tra phòng sau dọn — BR-HK-06, BR-HK-08.
 *
 * <p>Bảng append-only giống {@link RoomStatusHistoryRepository}: chỉ
 * {@code HousekeepingService.inspectTask} được ghi, không ai sửa hay xóa dòng đã ghi.
 *
 * <p>Mỗi task CHECKOUT chỉ đi qua bước kiểm tra ĐÚNG MỘT LẦN (kiểm xong task sang
 * {@code COMPLETED}, không quay lại {@code PENDING_INSPECTION} được), nên tra theo task luôn
 * trả về nhiều nhất một dòng.
 */
@Repository
public interface InspectionRecordRepository extends JpaRepository<InspectionRecord, UUID> {

    /**
     * Biên bản của một task. {@code tenantId} có mặt theo quy ước của codebase: mọi truy vấn
     * nghiệp vụ đều mang sẵn chốt cách ly Tenant, không phụ thuộc việc nơi gọi đã lọc hay chưa.
     */
    Optional<InspectionRecord> findByTenantIdAndTaskId(UUID tenantId, UUID taskId);
}
