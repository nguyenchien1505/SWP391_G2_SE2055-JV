package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.HousekeepingTaskReturn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Các lần trả lại việc dọn — BR-HK-17.
 *
 * <p>Bảng append-only giống {@link InspectionRecordRepository}: chỉ {@code HousekeepingService.returnTask}
 * được ghi, không ai sửa hay xóa dòng đã ghi.
 */
@Repository
public interface HousekeepingTaskReturnRepository extends JpaRepository<HousekeepingTaskReturn, UUID> {

    /** Lịch sử trả lại của một việc, mới nhất trước. {@code tenantId} là chốt cách ly Tenant như mọi truy vấn. */
    List<HousekeepingTaskReturn> findByTenantIdAndTaskIdOrderByReturnedAtDesc(UUID tenantId, UUID taskId);

    /**
     * Đếm gộp số lần trả của từng việc cho cả một trang việc dọn — tránh N+1. Việc chưa bị trả lần nào không
     * xuất hiện trong kết quả; bên gọi tự quy về 0. Không gọi với danh sách rỗng.
     */
    @Query("""
        SELECT r.taskId AS taskId, COUNT(r) AS total
        FROM HousekeepingTaskReturn r
        WHERE r.taskId IN :taskIds
        GROUP BY r.taskId
        """)
    List<TaskReturnCount> countGroupedByTask(@Param("taskIds") Collection<UUID> taskIds);

    interface TaskReturnCount {
        UUID getTaskId();
        long getTotal();
    }
}
