-- Open-task (BR Milestone 2, chốt 19/10/2026): nhân viên Dọn dẹp tự nhận việc chưa phân công
-- (BR-HK-02 bản sửa). BR-HK-12 (sửa): lưu CÁCH việc được giao — Manager gán, Giám sát gán, tự nhận.
--
-- Lưu trên việc dọn, không theo từng người trong nhóm (chốt 28/10/2026): đây là cách việc được BẮT ĐẦU
-- (Chưa phân công → Đang thực hiện), người bắt đầu vẫn là assigned_by. Thêm người vào nhóm sau đó không
-- đổi giá trị này. Việc quay về Chưa phân công thì xóa cùng assigned_by / assigned_at.
--
-- Không thêm CHECK "chưa phân công thì không có cách giao": V5 đã bỏ CHECK cùng loại
-- (ck_hk_assignment_pair) và để service giữ bất biến — vi phạm CHECK của MySQL ra 500 chứ không phải 409.

ALTER TABLE housekeeping_tasks
    ADD COLUMN assignment_source VARCHAR(20) NULL
        COMMENT 'MANAGER | SUPERVISION | SELF_CLAIM — BR-HK-12 (sửa M2)' AFTER assigned_at,
    ADD CONSTRAINT ck_hk_assignment_source CHECK (
        assignment_source IS NULL OR assignment_source IN ('MANAGER', 'SUPERVISION', 'SELF_CLAIM'));

-- Việc đã từng được giao (kể cả đã xong) điền theo người giao: trước M2 chỉ Manager giao; từ V8 Giám sát
-- (role STAFF) cũng giao được.
UPDATE housekeeping_tasks t
JOIN users u ON u.id = t.assigned_by
SET t.assignment_source = CASE WHEN u.role = 'STAFF' THEN 'SUPERVISION' ELSE 'MANAGER' END
WHERE t.assigned_by IS NOT NULL;
