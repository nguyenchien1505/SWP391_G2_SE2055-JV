package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.dto.LeaveAttachmentInfo;
import com.example.SWP391_G2_SE2055_JV.entity.LeaveRequestAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaveRequestAttachmentRepository extends JpaRepository<LeaveRequestAttachment, UUID> {

    /** Thông tin ảnh của nhiều đơn — một câu cho cả trang danh sách, KHÔNG đọc cột dữ liệu ảnh. */
    @Query("""
        select new com.example.SWP391_G2_SE2055_JV.dto.LeaveAttachmentInfo(
            a.id, a.leaveRequestId, a.fileName, a.contentType, a.sizeBytes)
        from LeaveRequestAttachment a
        where a.leaveRequestId in :leaveRequestIds
        order by a.createdAt, a.fileName
        """)
    List<LeaveAttachmentInfo> findInfoByLeaveRequestIds(@Param("leaveRequestIds") Collection<UUID> leaveRequestIds);

    long countByLeaveRequestId(UUID leaveRequestId);

    /** Chốt chặn cách ly Tenant, và ảnh phải đúng của đơn trên đường dẫn. */
    Optional<LeaveRequestAttachment> findByIdAndLeaveRequestIdAndTenantId(UUID id, UUID leaveRequestId, UUID tenantId);
}
