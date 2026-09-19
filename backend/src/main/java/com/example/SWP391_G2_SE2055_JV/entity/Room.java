package com.example.SWP391_G2_SE2055_JV.entity;

import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * Phòng khách sạn — BR-ROOM-01..10.
 *
 * <p>Phòng thuộc về một Location cụ thể; số phòng chỉ cần duy nhất trong phạm vi
 * Location đó (BR-ROOM-05). Ràng buộc unique được ép ở tầng DB bằng cột sinh
 * {@code active_room_number} — cột này CỐ Ý không map vào entity vì phòng đã xóa
 * mềm phải nhường lại số phòng.
 *
 * <p>Trạng thái đi theo ma trận {@link RoomStatus} (BR-ROOM-02); mỗi lần đổi sinh
 * đúng một dòng {@link RoomStatusHistory} (BR-ROOM-09).
 */
@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Room extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 36, nullable = false)
    private UUID tenantId;

    @Column(name = "location_id", length = 36, nullable = false)
    private UUID locationId;

    /** Duy nhất trong phạm vi Location, không phải toàn hệ thống — BR-ROOM-05. */
    @Column(name = "room_number", length = 20, nullable = false)
    private String roomNumber;

    /** Kiểu text chứ không phải số — chấp nhận G / M / B1 — BR-ROOM-05. */
    @Column(name = "floor", length = 10, nullable = false)
    private String floor;

    @Column(name = "room_type_id", length = 36, nullable = false)
    private UUID roomTypeId;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Column(name = "note", length = 500)
    private String note;

    /** 7 trạng thái, chuyển theo ma trận BR-ROOM-02 — BR-ROOM-01. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private RoomStatus status;

    /** Bắt buộc khi và chỉ khi status = UNAVAILABLE — BR-ROOM-07. */
    @Column(name = "unavailable_reason", length = 500)
    private String unavailableReason;

    /** BR-ROOM-08: xóa phòng là xóa mềm — FALSE nghĩa là đã xóa. */
    @Column(name = "is_active", nullable = false)
    @lombok.Builder.Default
    private boolean active = true;

    /** BR-ROOM-07: phòng Không khả dụng gộp cả Bảo trì lẫn Khóa phòng. */
    public boolean isUnavailable() {
        return status == RoomStatus.UNAVAILABLE;
    }

    /** BR-ROOM-08: chỉ xóa mềm được phòng đang Trống/Sẵn sàng hoặc Không khả dụng. */
    public boolean isDeletable() {
        return active && status != null && status.isDeletable();
    }
}
