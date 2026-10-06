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
     * Manager truyền {@code locationId}, nhân viên dọn truyền {@code staffId} — khi đó trả mọi
     * việc mà người đó có trong nhóm dọn. {@code assignedFrom} / {@code assignedTo} lọc ngày làm
     * theo khoảng (tính cả hai đầu) cho lịch dọn theo tuần.
     */
    @Query("""
        select t from HousekeepingTask t
        where t.tenantId = :tenantId
          and (:locationId   is null or t.locationId   = :locationId)
          and (:staffId      is null or :staffId member of t.assigneeIds)
          and (:status       is null or t.status       = :status)
          and (:taskType     is null or t.taskType     = :taskType)
          and (:assignedDate is null or t.assignedDate = :assignedDate)
          and (:assignedFrom is null or t.assignedDate >= :assignedFrom)
          and (:assignedTo   is null or t.assignedDate <= :assignedTo)
        """)
    Page<HousekeepingTask> search(@Param("tenantId") UUID tenantId,
                                  @Param("locationId") UUID locationId,
                                  @Param("staffId") UUID staffId,
                                  @Param("status") HousekeepingTaskStatus status,
                                  @Param("taskType") HousekeepingTaskType taskType,
                                  @Param("assignedDate") LocalDate assignedDate,
                                  @Param("assignedFrom") LocalDate assignedFrom,
                                  @Param("assignedTo") LocalDate assignedTo,
                                  Pageable pageable);

    /**
     * BR-HK-11: mỗi phòng tối đa 1 task ĐANG MỞ cho mỗi loại. DB đã chặn bằng unique
     * {@code open_task_key}; kiểm tra trước ở service để trả thông báo rõ ràng.
     */
    boolean existsByRoomIdAndTaskTypeAndStatusIn(UUID roomId, HousekeepingTaskType taskType,
                                                 Collection<HousekeepingTaskStatus> statuses);

    List<HousekeepingTask> findByRoomIdAndStatusIn(UUID roomId, Collection<HousekeepingTaskStatus> statuses);

    /** BR-HK-07 + BR-SCH-17: việc có người này trong nhóm dọn, ngày làm LỚN HƠN hôm nay. */
    @Query("""
        select t from HousekeepingTask t
        where :staffId member of t.assigneeIds
          and t.status = :status
          and t.assignedDate > :today
        """)
    List<HousekeepingTask> findOfStaffAfter(@Param("staffId") UUID staffId,
                                            @Param("status") HousekeepingTaskStatus status,
                                            @Param("today") LocalDate today);

    /** Việc có người này trong nhóm dọn, trong ĐÚNG một ngày, ở trạng thái cho trước — BR-HK-03. */
    @Query("""
        select t from HousekeepingTask t
        where :staffId member of t.assigneeIds
          and t.assignedDate = :date
          and t.status = :status
        """)
    List<HousekeepingTask> findOfStaffOn(@Param("staffId") UUID staffId,
                                         @Param("date") LocalDate date,
                                         @Param("status") HousekeepingTaskStatus status);

    /** Số việc có người này trong nhóm dọn, ở trạng thái cho trước (mọi ngày). */
    @Query("""
        select count(t) from HousekeepingTask t
        where :staffId member of t.assigneeIds
          and t.status = :status
        """)
    long countOfStaff(@Param("staffId") UUID staffId, @Param("status") HousekeepingTaskStatus status);
}
