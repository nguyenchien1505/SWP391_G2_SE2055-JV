package com.example.SWP391_G2_SE2055_JV.dto;

import java.util.UUID;

/**
 * Một ảnh đính kèm đơn nghỉ, KHÔNG kèm dữ liệu ảnh — màn hình tải ảnh riêng qua
 * {@code GET /scheduling/leave-requests/{leaveRequestId}/attachments/{id}}.
 */
public record LeaveAttachmentInfo(UUID id, UUID leaveRequestId, String fileName, String contentType, int sizeBytes) {}
