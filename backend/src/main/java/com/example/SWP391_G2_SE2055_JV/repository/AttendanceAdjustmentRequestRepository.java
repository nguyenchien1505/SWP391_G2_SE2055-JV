package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.AttendanceAdjustmentRequest;
import com.example.SWP391_G2_SE2055_JV.enums.AdjustmentRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/** Đơn điều chỉnh công. {@code tenantId} là chốt cách ly Tenant như mọi truy vấn. */
@Repository
public interface AttendanceAdjustmentRequestRepository extends JpaRepository<AttendanceAdjustmentRequest, UUID> {

    Optional<AttendanceAdjustmentRequest> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Khóa đơn khi duyệt / từ chối / hủy — hai quản lý (hay quản lý và người gửi) bấm cùng lúc đi lần lượt. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM AttendanceAdjustmentRequest r WHERE r.id = :id AND r.tenantId = :tenantId")
    Optional<AttendanceAdjustmentRequest> findForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    boolean existsByShiftIdAndStatus(UUID shiftId, AdjustmentRequestStatus status);

    Page<AttendanceAdjustmentRequest> findByTenantIdAndRequesterId(UUID tenantId, UUID requesterId, Pageable pageable);

    Page<AttendanceAdjustmentRequest> findByTenantIdAndLocationIdAndStatusIn(
        UUID tenantId, UUID locationId, Collection<AdjustmentRequestStatus> statuses, Pageable pageable);
}
