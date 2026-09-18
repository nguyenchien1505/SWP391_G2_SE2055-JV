package com.example.SWP391_G2_SE2055_JV.property.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Lịch sử chuyển trạng thái phòng (append-only)
 * Mỗi lần đổi {@code rooms.current_status_id} ghi 1 dòng ở đây trong cùng transaction;
 * {@code reason} lưu lý do (bảo trì/khóa, kết quả kiểm tra KHÔNG đạt...).
 */
@Entity
@Table(name = "room_status_history")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RoomStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    /** Trạng thái nguồn — null nếu là bản ghi đầu tiên của phòng. */
    @Column(name = "from_status_id")
    private Long fromStatusId;

    @Column(name = "to_status_id", nullable = false)
    private Long toStatusId;

    /** Người thực hiện — null nếu do hệ thống tự động (vd DIRTY→CLEANING khi phân công). */
    @Column(name = "changed_by")
    private Long changedBy;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Column(length = 500)
    private String reason;
}
