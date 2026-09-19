package com.example.SWP391_G2_SE2055_JV.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Bộ cột audit dùng chung cho MỌI bảng — DM-17.
 *
 * <p>Milestone 1 KHÔNG có bảng AuditLog chung (BR-OUT-01); dấu vết tạo/sửa nằm ở
 * 4 cột này trên từng bảng, cộng với RoomStatusHistory và InspectionRecord cho
 * nghiệp vụ phòng.
 *
 * <p>Cả 4 cột do Spring Data JPA auditing tự điền — xem {@code JpaConfig.auditorAware()}.
 * Service KHÔNG set tay.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public abstract class AuditableEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @CreatedBy
    @Column(name = "created_by", length = 36, updatable = false)
    private UUID createdBy;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by", length = 36)
    private UUID updatedBy;
}
