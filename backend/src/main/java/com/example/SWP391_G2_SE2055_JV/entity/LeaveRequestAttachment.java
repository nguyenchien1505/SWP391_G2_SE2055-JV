package com.example.SWP391_G2_SE2055_JV.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * Ảnh minh chứng đính kèm lý do xin nghỉ (chốt 01/11/2026, thay BR-SCH-30). Người gửi đính kèm khi đơn còn chờ duyệt;
 * người gửi và người duyệt theo cấp (BR-SCH-08) xem được.
 *
 * <p>Danh sách đơn KHÔNG nạp entity này — chỉ đọc thông tin ảnh qua
 * {@code LeaveRequestAttachmentRepository.findInfoByLeaveRequestIds}, để không kéo cột {@link #data} lên.
 */
@Entity
@Table(name = "leave_request_attachments")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class LeaveRequestAttachment extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 36, nullable = false)
    private UUID tenantId;

    @Column(name = "leave_request_id", length = 36, nullable = false)
    private UUID leaveRequestId;

    @Column(name = "file_name", length = 255, nullable = false)
    private String fileName;

    /** Đọc từ chính các byte đầu tệp lúc nhận ảnh — không tin đuôi tên tệp hay Content-Type trình duyệt gửi. */
    @Column(name = "content_type", length = 30, nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @JdbcTypeCode(SqlTypes.LONGVARBINARY)
    @Column(name = "data", nullable = false, columnDefinition = "MEDIUMBLOB")
    private byte[] data;
}
