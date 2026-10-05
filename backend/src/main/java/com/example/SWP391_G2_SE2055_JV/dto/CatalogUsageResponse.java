package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.enums.PositionType;
import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import com.example.SWP391_G2_SE2055_JV.enums.UserStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Những gì đang dùng một mục danh mục — chính là thứ chặn việc xóa mục đó (BR-ORG-10,
 * BR-ROOM-08). Màn Danh mục của Giám đốc hiện danh sách này khi bấm vào tên một mục.
 *
 * <p>Chỉ MỘT danh sách có dữ liệu, tùy loại danh mục:
 * <ul>
 *   <li>Loại phòng → {@link #rooms}: mọi phòng thuộc loại, TÍNH CẢ phòng đã xóa mềm.</li>
 *   <li>Phòng ban → {@link #positions}: mọi vị trí thuộc phòng ban, kèm số nhân viên.</li>
 *   <li>Vị trí → {@link #staff}: mọi nhân viên giữ vị trí, TÍNH CẢ người đã nghỉ việc.</li>
 * </ul>
 */
@Data
@Builder
public class CatalogUsageResponse {

    private List<RoomRef>     rooms;
    private List<PositionRef> positions;
    private List<StaffRef>    staff;

    /** {@code active = false}: phòng đã xóa mềm — vẫn chặn xóa loại phòng. */
    public record RoomRef(UUID id, String roomNumber, String floor, UUID locationId, String locationName,
                          RoomStatus status, boolean active) {}

    /** Số nhân viên đang làm (gồm cả đang tạm khóa) và đã nghỉ việc giữ vị trí này. */
    public record PositionRef(UUID id, String name, PositionType positionType, boolean active,
                              long workingStaff, long terminatedStaff) {}

    public record StaffRef(UUID id, String fullName, String email, UUID locationId, String locationName,
                           UserStatus status, LocalDate startWorkDate) {}
}
