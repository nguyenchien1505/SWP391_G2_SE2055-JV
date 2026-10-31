package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.ShiftAttendanceEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Các lần ghi chấm công — append-only giống {@link HousekeepingTaskReturnRepository}: chỉ
 * {@code AttendanceService} được ghi, không ai sửa hay xóa dòng đã ghi.
 */
@Repository
public interface ShiftAttendanceEventRepository extends JpaRepository<ShiftAttendanceEvent, UUID> {

    /** Lịch sử chấm công của một ca, cũ nhất trước. {@code tenantId} là chốt cách ly Tenant. */
    List<ShiftAttendanceEvent> findByTenantIdAndShiftIdOrderByRecordedAtAsc(UUID tenantId, UUID shiftId);

    /** Mọi lần ghi của cả một trang ca, một truy vấn — tránh N+1. Không gọi với danh sách rỗng. */
    List<ShiftAttendanceEvent> findByShiftIdInOrderByRecordedAtAsc(Collection<UUID> shiftIds);
}
