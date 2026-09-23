package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.Valid;
import lombok.Data;

import java.util.UUID;

/**
 * Cho nghỉ việc kèm bàn giao khách sạn.
 *
 * <p>Manager đang phụ trách khách sạn nghỉ việc thì PHẢI có người nhận thay ngay trong cùng
 * thao tác, để khách sạn không rơi vào cảnh không người quản lý. Gửi đúng MỘT trong hai:
 * <ul>
 *   <li>{@code replacementManagerId} — một Manager dự bị (chưa gán khách sạn, đang hoạt động).</li>
 *   <li>{@code newManager} — hồ sơ Manager mới; {@code role} và {@code locationId} gửi lên bị
 *       bỏ qua, luôn là MANAGER của khách sạn người nghỉ việc đang phụ trách.</li>
 * </ul>
 *
 * <p>Staff và Manager dự bị nghỉ việc thì không cần bàn giao — để trống cả hai.
 */
@Data
public class TerminateUserRequest {

    private UUID replacementManagerId;

    @Valid
    private CreateUserRequest newManager;
}
