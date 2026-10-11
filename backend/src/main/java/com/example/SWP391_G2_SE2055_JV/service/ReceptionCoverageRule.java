package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.entity.ShiftTemplate;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.StaffPermission;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftRepository;
import com.example.SWP391_G2_SE2055_JV.repository.ShiftTemplateRepository;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Mỗi ca theo mẫu đã có người thì phải có ít nhất MỘT lễ tân — chốt 05/10/2026, chặn cứng (ghi
 * vào THAY_DOI_BR). "Một ca" là một mẫu ca trong một ngày ở một khách sạn, ví dụ "Ca sáng 05/10".
 *
 * <ul>
 *   <li>"Lễ tân" = nhân viên có quyền Lễ tân (quyền tick theo từng người — THAY_DOI_BR), kể cả người
 *       kiêm Dọn dẹp.</li>
 *   <li>Ca tự nhập giờ (không theo mẫu — hàng "Ca giờ khác") không bắt buộc: thường là ca hỗ trợ.</li>
 *   <li>Chỗ trống chưa giao người không tính là người.</li>
 * </ul>
 *
 * <p>Hai hướng chặn, để Manager luôn sửa được lịch chứ không bị khóa cứng:
 * <ul>
 *   <li><b>Thêm người</b> vào một ca (tạo ca, giao người, dời ca sang ca khác): SAU thao tác ca phải
 *       có lễ tân.</li>
 *   <li><b>Bớt người</b> khỏi một ca (gỡ người, xóa ca, dời ca đi): không được làm một ca ĐANG có lễ
 *       tân thành hết lễ tân trong khi vẫn còn người khác. Ca VỐN đã thiếu lễ tân — ví dụ lễ tân
 *       vừa nghỉ việc, hệ thống tự gỡ ca (BR-USER-04), không chặn được — thì vẫn cho bớt người.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ReceptionCoverageRule {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ShiftRepository         shiftRepository;
    private final UserRepository          userRepository;
    private final ShiftTemplateRepository shiftTemplateRepository;

    /**
     * Thêm {@code joining} vào ca ({@code locationId}, {@code date}, {@code templateId}) — sau đó ca
     * phải có lễ tân. Không theo mẫu ({@code templateId = null}) hoặc không thêm ai thì bỏ qua.
     */
    public void assertCoveredAfterAdding(UUID locationId, LocalDate date, UUID templateId,
                                         Collection<UUID> joining) {
        if (templateId == null || joining.isEmpty()) {
            return;
        }
        Set<UUID> crew = crewOf(locationId, date, templateId, null);
        crew.addAll(joining);
        if (!hasReceptionist(crew)) {
            throw new BusinessException(String.format(
                "%s ngày %s phải có ít nhất 1 lễ tân (người có quyền Lễ tân). Chọn thêm một lễ tân cho ca này.",
                templateName(templateId), date.format(DAY)));
        }
    }

    /**
     * Người của {@code leaving} rời ca (gỡ, xóa, hoặc dời sang ngày/ca khác). Gọi TRƯỚC khi đổi ca, lúc
     * {@code leaving} vẫn còn giữ ngày và mẫu cũ.
     */
    public void assertStillCoveredAfterRemoving(Shift leaving) {
        if (leaving.getSourceTemplateId() == null || leaving.getStaffId() == null) {
            return;
        }
        Set<UUID> before = crewOf(leaving.getLocationId(), leaving.getShiftDate(), leaving.getSourceTemplateId(), null);
        Set<UUID> after = crewOf(leaving.getLocationId(), leaving.getShiftDate(), leaving.getSourceTemplateId(),
            leaving.getId());
        if (after.isEmpty() || !hasReceptionist(before) || hasReceptionist(after)) {
            return;
        }
        String name = userRepository.findById(leaving.getStaffId())
            .map(User::getFullName)
            .orElse("Người này");
        throw new BusinessException(String.format(
            "%s là lễ tân duy nhất của %s ngày %s. Giao thêm một lễ tân khác cho ca này trước, rồi mới "
                + "gỡ, xóa hoặc dời ca của %s.",
            name, templateName(leaving.getSourceTemplateId()), leaving.getShiftDate().format(DAY), name));
    }

    /** Người đang có trong ca, trừ slot {@code excludeShiftId} (nếu có). */
    private Set<UUID> crewOf(UUID locationId, LocalDate date, UUID templateId, UUID excludeShiftId) {
        Set<UUID> crew = new HashSet<>();
        for (Shift shift : shiftRepository.findByLocationIdAndShiftDateAndSourceTemplateId(locationId, date, templateId)) {
            if (shift.getStaffId() != null && !Objects.equals(shift.getId(), excludeShiftId)) {
                crew.add(shift.getStaffId());
            }
        }
        return crew;
    }

    private boolean hasReceptionist(Set<UUID> staffIds) {
        return !staffIds.isEmpty() && userRepository.findAllById(staffIds).stream()
            .anyMatch(user -> user.getPermissions().contains(StaffPermission.RECEPTION));
    }

    private String templateName(UUID templateId) {
        return shiftTemplateRepository.findById(templateId).map(ShiftTemplate::getName).orElse("Ca này");
    }
}
