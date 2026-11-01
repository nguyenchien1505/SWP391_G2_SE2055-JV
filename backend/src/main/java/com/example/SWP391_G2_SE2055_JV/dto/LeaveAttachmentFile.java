package com.example.SWP391_G2_SE2055_JV.dto;

/** Một ảnh đính kèm đơn nghỉ kèm dữ liệu — để controller trả về đúng kiểu ảnh. */
public record LeaveAttachmentFile(String fileName, String contentType, byte[] data) {}
