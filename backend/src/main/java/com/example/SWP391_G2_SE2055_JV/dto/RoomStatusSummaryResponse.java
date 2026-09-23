package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import com.example.SWP391_G2_SE2055_JV.repository.RoomRepository.RoomStatusCount;
import lombok.Builder;
import lombok.Data;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Số phòng theo từng trạng thái — BR-DASH-02 (toàn Tenant), BR-DASH-03 (một Location).
 *
 * <p>{@code counts} LUÔN đủ 7 khóa theo thứ tự khai báo của {@link RoomStatus}, trạng thái
 * không có phòng nào mang giá trị 0 — frontend vẽ thẳng 7 thẻ số liệu, không phải tự bù.
 */
@Data
@Builder
public class RoomStatusSummaryResponse {

    /** {@code null} nghĩa là đếm trên toàn Tenant (chỉ Giám đốc mới có trường hợp này). */
    private UUID                  locationId;
    private long                  total;
    private Map<RoomStatus, Long> counts;

    /**
     * Ghép kết quả GROUP BY (chỉ có các trạng thái đang có phòng) thành bảng đủ 7 trạng thái.
     * Dùng {@link EnumMap} để thứ tự khóa khi ra JSON đúng thứ tự enum.
     */
    public static RoomStatusSummaryResponse of(UUID locationId, Collection<RoomStatusCount> rows) {
        Map<RoomStatus, Long> counts = new EnumMap<>(RoomStatus.class);
        for (RoomStatus status : RoomStatus.values()) {
            counts.put(status, 0L);
        }
        long total = 0;
        for (RoomStatusCount row : rows) {
            counts.put(row.getStatus(), row.getTotal());
            total += row.getTotal();
        }
        return RoomStatusSummaryResponse.builder()
            .locationId(locationId)
            .total(total)
            .counts(counts)
            .build();
    }
}
