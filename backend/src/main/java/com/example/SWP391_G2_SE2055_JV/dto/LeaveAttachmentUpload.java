package com.example.SWP391_G2_SE2055_JV.dto;

/**
 * Một tệp người gửi đơn tải lên — controller đọc từ multipart rồi chuyển vào service, để service không phụ thuộc
 * kiểu của tầng web.
 *
 * @param fileName tên tệp trình duyệt gửi, chưa làm sạch
 */
public record LeaveAttachmentUpload(String fileName, byte[] data) {}
