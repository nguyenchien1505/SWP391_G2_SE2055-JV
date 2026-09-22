package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.ChangeRoomStatusRequest;
import com.example.SWP391_G2_SE2055_JV.entity.Room;
import com.example.SWP391_G2_SE2055_JV.entity.RoomStatusHistory;
import com.example.SWP391_G2_SE2055_JV.enums.ChangeSource;
import com.example.SWP391_G2_SE2055_JV.enums.InspectionResult;
import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.repository.RoomRepository;
import com.example.SWP391_G2_SE2055_JV.repository.RoomStatusHistoryRepository;
import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Trạng thái phòng — BR-ROOM-02, BR-ROOM-03, BR-ROOM-07, BR-ROOM-09, BR-ROOM-10, BR-HK-09.
 *
 * <p><b>Nơi DUY NHẤT ghi {@code rooms.status}.</b> Mỗi lần đổi ghi đúng một dòng
 * {@link RoomStatusHistory} trong CÙNG transaction (BR-ROOM-09): lỗi ở bất kỳ bước nào thì cả
 * trạng thái lẫn lịch sử cùng rollback. Không class nào khác được {@code room.setStatus(...)}.
 *
 * <p>Nhận thẳng entity {@link Room}: bên gọi đã tải phòng trong phạm vi Tenant/Location của
 * người dùng ({@code RoomService.getOwnedRoom}, sau này {@code HousekeepingService}).
 *
 * <p>Phụ thuộc {@link HousekeepingRoomHooks}, KHÔNG phụ thuộc {@code HousekeepingService}: ở giai
 * đoạn 2, HousekeepingService sẽ gọi lớp này, nên chiều ngược lại sẽ tạo vòng bean.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomStatusService {

    private final RoomRepository              roomRepository;
    private final RoomStatusHistoryRepository historyRepository;
    private final RoomTransitionPolicy        transitionPolicy;
    private final HousekeepingRoomHooks       hooks;

    // ── Người dùng bấm: PATCH /rooms/{id}/status ────────────────────────────

    /**
     * Đổi trạng thái theo yêu cầu của người đang đăng nhập. Thứ tự kiểm tra cố định để lỗi
     * trả về luôn nhất quán:
     * <ol>
     *   <li>Bước chuyển có trong ma trận BR-ROOM-02 không → 400.</li>
     *   <li>Có phải bước chỉ xảy ra qua task dọn không → 400.</li>
     *   <li>Người này có được bấm bước này không → 403.</li>
     *   <li>Khóa phòng thì phải có lý do (BR-ROOM-07) → 400.</li>
     * </ol>
     * Qua hết thì đổi trạng thái + ghi lịch sử, rồi áp hệ quả lên task dọn.
     */
    @Transactional
    public Room changeStatusByUser(Room room, ChangeRoomStatusRequest request) {
        RoomStatus from = room.getStatus();
        RoomStatus target = request.getTargetStatus();

        assertInMatrix(from, target);
        ChangeSource source = transitionPolicy.authorize(from, target);
        String reason = normalize(request.getReason());
        if (target == RoomStatus.UNAVAILABLE && reason == null) {
            throw new BusinessException("Phải nhập lý do khi chuyển phòng sang Không khả dụng.");
        }

        changeStatus(room, target, source, reason, null);
        applyTaskConsequences(room, from, target);
        log.info("Phòng {}: {} → {} ({})", room.getRoomNumber(), from, target, source);
        return room;
    }

    /**
     * Hệ quả lên task dọn, gắn theo SỰ KIỆN của phòng chứ không theo người bấm — bảng "Liên kết
     * với task dọn" của 01-state-machine:
     * <ul>
     *   <li>Vào Không khả dụng → hủy MỌI task đang mở (BR-HK-09).</li>
     *   <li>Rời Đang sử dụng (check-out) → hủy task dọn hằng ngày đang mở (BR-HK-10). Phải chạy
     *       TRƯỚC khi sinh task mới — thứ tự do {@link HousekeepingRoomHooks} quy định.</li>
     *   <li>Vào Chờ dọn (check-out, mở khóa về Chờ dọn) → sinh task CHECKOUT (BR-HK-01, BR-HK-09).</li>
     * </ul>
     * Các hàm dành cho Housekeeping bên dưới KHÔNG đi qua đây: bên đó tự lo task của mình.
     */
    private void applyTaskConsequences(Room room, RoomStatus from, RoomStatus target) {
        if (target == RoomStatus.UNAVAILABLE) {
            hooks.onRoomBecameUnavailable(room);
            return;
        }
        if (from == RoomStatus.OCCUPIED) {
            hooks.onGuestCheckedOut(room);
        }
        if (target == RoomStatus.DIRTY) {
            hooks.onRoomBecameDirty(room);
        }
    }

    // ── Phòng mới tạo: dùng ở F3 ────────────────────────────────────────────

    /**
     * BR-ROOM-10: phòng mới tạo vào thẳng Chờ dọn — ghi dòng lịch sử đầu tiên ({@code from = null},
     * nguồn hệ thống) và sinh task dọn chưa phân công (BR-HK-01). Gọi NGAY SAU khi lưu phòng,
     * trong cùng transaction tạo phòng.
     */
    @Transactional
    public void recordInitialStatus(Room room) {
        if (room.getStatus() != RoomStatus.DIRTY) {
            throw new IllegalStateException("Phòng mới tạo phải ở trạng thái DIRTY (BR-ROOM-10)");
        }
        historyRepository.save(historyRow(room, null, ChangeSource.SYSTEM, null, null));
        hooks.onRoomBecameDirty(room);
    }

    // ── API cho Housekeeping (giai đoạn 2 — F5, F6) ─────────────────────────
    // Wrapper mỏng quanh changeStatus: mỗi hàm là đúng MỘT bước chuyển, kiểm tra trạng thái
    // nguồn cụ thể và không gọi hook. Phòng sai trạng thái → 400, cả transaction của
    // Housekeeping rollback, không để lại task lệch với phòng.

    /** Manager phân công task CHECKOUT: Chờ dọn → Đang dọn, nguồn hệ thống (BR-ROOM-02). */
    @Transactional
    public void startCleaning(Room room, UUID taskId) {
        transition(room, RoomStatus.DIRTY, RoomStatus.CLEANING, ChangeSource.SYSTEM, null, taskId);
    }

    /** Nhân viên dọn bấm hoàn thành task CHECKOUT: Đang dọn → Chờ kiểm tra (BR-ROOM-02, BR-PERM-05). */
    @Transactional
    public void markPendingInspection(Room room, UUID taskId) {
        transition(room, RoomStatus.CLEANING, RoomStatus.INSPECTION, ChangeSource.HOUSEKEEPING, null, taskId);
    }

    /** Task đang làm bị gỡ người: Đang dọn → Chờ dọn, nguồn hệ thống (BR-HK-07). Task cũ vẫn mở. */
    @Transactional
    public void revertToDirty(Room room, UUID taskId, String reason) {
        transition(room, RoomStatus.CLEANING, RoomStatus.DIRTY, ChangeSource.SYSTEM, normalize(reason), taskId);
    }

    /**
     * Manager kiểm tra phòng: ĐẠT → Trống/Sẵn sàng, KHÔNG ĐẠT → Chờ dọn (BR-ROOM-02, BR-HK-06).
     * Task dọn lại khi không đạt do Housekeeping tự sinh (BR-HK-12), nên ở đây không sinh.
     */
    @Transactional
    public void applyInspection(Room room, InspectionResult result, String reason, UUID taskId) {
        RoomStatus target = result == InspectionResult.PASS ? RoomStatus.AVAILABLE : RoomStatus.DIRTY;
        transition(room, RoomStatus.INSPECTION, target, ChangeSource.MANAGER, normalize(reason), taskId);
    }

    // ── Lõi ─────────────────────────────────────────────────────────────────

    /** Bước chuyển chỉ hợp lệ khi phòng đang ĐÚNG trạng thái nguồn mà bước đó đòi hỏi. */
    private void transition(Room room, RoomStatus expectedFrom, RoomStatus target,
                            ChangeSource source, String reason, UUID taskId) {
        if (room.getStatus() != expectedFrom) {
            throw new BusinessException(String.format(
                "Phòng %s đang ở trạng thái «%s», không chuyển sang «%s» được — cần «%s».",
                room.getRoomNumber(), room.getStatus().label(), target.label(), expectedFrom.label()));
        }
        changeStatus(room, target, source, reason, taskId);
    }

    /**
     * Đổi trạng thái + ghi ĐÚNG một dòng lịch sử (BR-ROOM-09). Luôn chạy bên trong transaction
     * của hàm public gọi nó.
     *
     * <p>{@code unavailable_reason} có giá trị KHI VÀ CHỈ KHI phòng đang Không khả dụng (DB ép
     * bằng {@code ck_rooms_unavailable_reason}): gán ở đây cho mọi bước chuyển, nên ra khỏi trạng
     * thái đó là lý do tự được xóa. Đừng gán trường này ở nơi khác.
     */
    private void changeStatus(Room room, RoomStatus target, ChangeSource source,
                              String reason, UUID relatedTaskId) {
        RoomStatus from = room.getStatus();
        assertInMatrix(from, target);

        room.setStatus(target);
        room.setUnavailableReason(target == RoomStatus.UNAVAILABLE ? reason : null);
        roomRepository.save(room);
        historyRepository.save(historyRow(room, from, source, reason, relatedTaskId));
    }

    /** Dòng lịch sử cho trạng thái HIỆN TẠI của phòng. Nguồn hệ thống thì không có người thực hiện. */
    private static RoomStatusHistory historyRow(Room room, RoomStatus from, ChangeSource source,
                                                String reason, UUID relatedTaskId) {
        return RoomStatusHistory.builder()
            .tenantId(room.getTenantId())
            .roomId(room.getId())
            .fromStatus(from)
            .toStatus(room.getStatus())
            .changedBy(source == ChangeSource.SYSTEM ? null : SecurityUtils.getCurrentUserId())
            .changedAt(LocalDateTime.now())
            .changeSource(source)
            .reason(reason)
            .relatedTaskId(relatedTaskId)
            .build();
    }

    /** BR-ROOM-02: mọi bước ngoài ma trận bị chặn, kể cả "chuyển" sang chính trạng thái đang có. */
    private static void assertInMatrix(RoomStatus from, RoomStatus target) {
        if (!from.canTransitionTo(target)) {
            throw new BusinessException(String.format(
                "Không thể chuyển phòng từ «%s» sang «%s».", from.label(), target.label()));
        }
    }

    /** Ô lý do để trống hoặc toàn khoảng trắng coi như không nhập. */
    private static String normalize(String reason) {
        return StringUtils.hasText(reason) ? reason.trim() : null;
    }
}
