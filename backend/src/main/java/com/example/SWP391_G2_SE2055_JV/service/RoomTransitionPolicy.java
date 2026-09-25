package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.enums.ChangeSource;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import com.example.SWP391_G2_SE2055_JV.enums.StaffPermission;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.UnauthorizedException;
import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * "Ai được bấm bước chuyển nào" qua {@code PATCH /rooms/{id}/status} — BR-ROOM-02, BR-ROOM-03,
 * BR-PERM-04. Đây là NGUỒN DUY NHẤT của chính sách này, dùng cho hai việc:
 * <ul>
 *   <li>CHẶN: {@link #authorize} trước khi {@link RoomStatusService} đổi trạng thái.</li>
 *   <li>HIỂN THỊ: {@link #allowedTargetsForCurrentUser} tính {@code RoomResponse.allowedTargets}
 *       để frontend vẽ nút mà không phải chép lại bảng quyền.</li>
 * </ul>
 * Hai việc cùng đọc một bộ luật nên nút hiện ra luôn khớp với thứ backend cho phép.
 *
 * <p>Ma trận hợp lệ (bước nào tồn tại) nằm ở {@link RoomStatus}; lớp này chỉ trả lời câu hỏi
 * về NGƯỜI BẤM, và không đụng tới dữ liệu.
 */
@Component
public class RoomTransitionPolicy {

    /**
     * Bước 2 và 3 của thứ tự kiểm tra (bước 1 — ma trận — do {@link RoomStatusService} làm).
     *
     * @return tư cách người đang đăng nhập thực hiện bước chuyển, để ghi vào lịch sử
     * @throws BusinessException     400 nếu bước chuyển chỉ xảy ra qua thao tác trên task dọn
     * @throws UnauthorizedException 403 nếu người đang đăng nhập không được bấm bước này
     */
    public ChangeSource authorize(RoomStatus from, RoomStatus target) {
        if (isTaskDriven(from, target)) {
            throw new BusinessException(String.format(
                "Phòng chỉ chuyển từ «%s» sang «%s» qua thao tác trên task dọn phòng, "
                    + "không đổi trực tiếp được.", from.label(), target.label()));
        }
        return actingAs(from, target).orElseThrow(() -> new UnauthorizedException(String.format(
            "Bạn không có quyền chuyển phòng từ «%s» sang «%s».",
            from.label(), target.label())));
    }

    /** Các đích hợp lệ theo ma trận mà người đang đăng nhập bấm được — nguồn của {@code allowedTargets}. */
    public Set<RoomStatus> allowedTargetsForCurrentUser(RoomStatus from) {
        return from.allowedTargets().stream()
            .filter(target -> !isTaskDriven(from, target) && actingAs(from, target).isPresent())
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(RoomStatus.class)));
    }

    /**
     * Bước chuyển là HỆ QUẢ của thao tác trên task dọn (assign, hoàn thành, kiểm tra, gỡ người) —
     * không ai bấm thẳng được, kể cả Manager: kiểm tra phòng phải đi qua endpoint kiểm tra để có
     * biên bản (BR-HK-06).
     *
     * <p>Ngoại lệ: đưa phòng Đang dọn / Chờ kiểm tra vào Không khả dụng là Manager KHÓA PHÒNG
     * (BR-ROOM-03), nên phép thử {@code UNAVAILABLE} phải đứng trước.
     */
    private static boolean isTaskDriven(RoomStatus from, RoomStatus target) {
        if (target == RoomStatus.UNAVAILABLE) {
            return false;
        }
        return target == RoomStatus.CLEANING || target == RoomStatus.INSPECTION
            || from == RoomStatus.CLEANING || from == RoomStatus.INSPECTION;
    }

    /**
     * Người đang đăng nhập bấm bước chuyển với tư cách nào; rỗng = không được bấm.
     *
     * <p>Chốt 20/09/2026: Manager KHÔNG làm thay Lễ tân. Muốn mở (Lễ tân nghỉ đột xuất) thì chỉ
     * sửa hàm này, ví dụ cho {@code Role.MANAGER} dùng thêm {@link #isReceptionStep}.
     */
    private static Optional<ChangeSource> actingAs(RoomStatus from, RoomStatus target) {
        if (SecurityUtils.hasPermission(StaffPermission.RECEPTION) && isReceptionStep(from, target)) {
            return Optional.of(ChangeSource.RECEPTION);
        }
        if (SecurityUtils.hasRole(Role.MANAGER) && isManagerStep(from, target)) {
            return Optional.of(ChangeSource.MANAGER);
        }
        return Optional.empty();
    }

    /** BR-ROOM-02, BR-PERM-04: đặt trước, check-in, check-out, hủy đặt / no-show. */
    private static boolean isReceptionStep(RoomStatus from, RoomStatus target) {
        return switch (target) {
            case RESERVED, OCCUPIED -> true;
            case DIRTY -> from == RoomStatus.OCCUPIED;
            case AVAILABLE -> from == RoomStatus.RESERVED;
            default -> false;
        };
    }

    /** BR-ROOM-03: chỉ Manager đưa phòng vào / ra Không khả dụng. */
    private static boolean isManagerStep(RoomStatus from, RoomStatus target) {
        return target == RoomStatus.UNAVAILABLE || from == RoomStatus.UNAVAILABLE;
    }
}
