package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.HousekeepingTask;
import com.example.SWP391_G2_SE2055_JV.enums.HousekeepingTaskStatus;
import com.example.SWP391_G2_SE2055_JV.enums.HousekeepingTaskType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HousekeepingTaskRepository extends JpaRepository<HousekeepingTask, UUID> {

    Optional<HousekeepingTask> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Lịch dọn. Tham số nào {@code null} thì bỏ qua điều kiện đó; {@code tenantId} luôn
     * bắt buộc để cách ly dữ liệu. Phạm vi theo vai trò do service quyết định:
     * Manager truyền {@code locationId}, nhân viên dọn truyền {@code staffId}.
     */
    @Query("""
        select t from HousekeepingTask t
        where t.tenantId = :tenantId
          and (:locationId   is null or t.locationId      = :locationId)
          and (:staffId      is null or t.assignedStaffId = :staffId)
          and (:status       is null or t.status          = :status)
          and (:taskType     is null or t.taskType        = :taskType)
          and (:assignedDate is null or t.assignedDate    = :assignedDate)
        """)
    Page<HousekeepingTask> search(@Param("tenantId") UUID tenantId,
                                  @Param("locationId") UUID locationId,
                                  @Param("staffId") UUID staffId,
                                  @Param("status") HousekeepingTaskStatus status,
                                  @Param("taskType") HousekeepingTaskType taskType,
                                  @Param("assignedDate") LocalDate assignedDate,
                                  Pageable pageable);

    /**
     * BR-HK-11: mỗi phòng tối đa 1 task ĐANG MỞ cho mỗi loại. DB đã chặn bằng unique
     * {@code open_task_key}; kiểm tra trước ở service để trả thông báo rõ ràng.
     */
    boolean existsByRoomIdAndTaskTypeAndStatusIn(UUID roomId, HousekeepingTaskType taskType,
                                                 Collection<HousekeepingTaskStatus> statuses);

    List<HousekeepingTask> findByRoomIdAndStatusIn(UUID roomId, Collection<HousekeepingTaskStatus> statuses);

    List<HousekeepingTask> findByRoomIdAndTaskTypeAndStatusIn(UUID roomId, HousekeepingTaskType taskType,
                                                              Collection<HousekeepingTaskStatus> statuses);

    /** BR-HK-07 + BR-SCH-17: task đã gán cho ngày LỚN HƠN hôm nay của một nhân viên. */
    List<HousekeepingTask> findByAssignedStaffIdAndStatusAndAssignedDateGreaterThan(
        UUID staffId, HousekeepingTaskStatus status, LocalDate today);
}
