-- Cho xóa được chỗ trống từng có yêu cầu đổi ca (chốt 09/11/2026). Trước đây khóa ngoại tới shifts không có ON DELETE
-- và 2 cột NOT NULL, nên ca từng nằm trong bất kỳ yêu cầu nào (kể cả đã hủy / bị từ chối) không xóa được — chỗ trống
-- nằm mãi trên lịch. Nay khi xóa ca, ứng dụng gỡ liên kết ca ở các yêu cầu cũ (đặt NULL) rồi mới xóa: yêu cầu vẫn giữ
-- làm lịch sử, giao diện ghi «Ca không còn».
--
-- Khóa ngoại giữ RESTRICT như cũ (lưới an toàn: quên gỡ liên kết thì DB vẫn chặn). Không dùng ON DELETE SET NULL vì
-- 2 cột này là gốc của 2 cột sinh pending_*_shift_id có UNIQUE index, mà MySQL hạn chế SET NULL trên loại cột đó.
-- ck_swap_distinct vẫn đúng: so sánh với NULL ra UNKNOWN, CHECK của MySQL cho qua.
ALTER TABLE shift_swap_requests
    DROP FOREIGN KEY fk_swap_requester_shift,
    DROP FOREIGN KEY fk_swap_peer_shift;

ALTER TABLE shift_swap_requests
    MODIFY requester_shift_id CHAR(36) NULL COMMENT 'NULL = ca đã bị xóa sau khi yêu cầu khép lại',
    MODIFY peer_shift_id      CHAR(36) NULL COMMENT 'NULL = ca đã bị xóa sau khi yêu cầu khép lại';

ALTER TABLE shift_swap_requests
    ADD CONSTRAINT fk_swap_requester_shift FOREIGN KEY (requester_shift_id) REFERENCES shifts (id),
    ADD CONSTRAINT fk_swap_peer_shift      FOREIGN KEY (peer_shift_id)      REFERENCES shifts (id);
