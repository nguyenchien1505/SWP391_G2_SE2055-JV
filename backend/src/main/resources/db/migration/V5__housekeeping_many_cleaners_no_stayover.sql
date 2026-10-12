-- Hai thay đổi nghiệp vụ của lịch dọn phòng (chốt 05/10/2026 — ghi vào docs/THAY_DOI_BR.docx):
--
-- 1) BỎ việc dọn hằng ngày (STAYOVER) — thay BR-HK-05, BR-HK-10, phần STAYOVER của BR-HK-06,
--    BR-HK-11, BR-HK-12. Chỉ còn việc dọn sau khi khách trả phòng (CHECKOUT), hệ thống tự sinh.
--    Việc dọn hằng ngày đã có trong DB bị XÓA: chúng không bao giờ kéo theo trạng thái phòng
--    (BR-HK-05) nên không có lịch sử phòng hay biên bản kiểm tra nào thật sự dựa vào chúng —
--    vài câu dọn tham chiếu bên dưới chỉ để chắc chắn khóa ngoại không chặn.
--
-- 2) MỘT PHÒNG NHIỀU NGƯỜI DỌN — thay DM-04 (một việc dọn gắn đúng một người). Người làm của một
--    việc dọn chuyển từ cột housekeeping_tasks.assigned_staff_id sang bảng housekeeping_task_assignees.
--    Ngày làm (assigned_date) vẫn ở việc dọn: cả nhóm dọn cùng một ngày, mỗi người phải có ca
--    ngày đó (BR-HK-03). Bất kỳ ai trong nhóm bấm "Hoàn thành" là xong cho cả nhóm.

-- ── 1) Bỏ việc dọn hằng ngày ─────────────────────────────────────────────────
UPDATE room_status_history
SET related_task_id = NULL
WHERE related_task_id IN (SELECT id FROM (
    SELECT id FROM housekeeping_tasks WHERE task_type = 'STAYOVER') AS stayover);

DELETE FROM inspection_records
WHERE task_id IN (SELECT id FROM (
    SELECT id FROM housekeeping_tasks WHERE task_type = 'STAYOVER') AS stayover);

UPDATE inspection_records
SET next_task_id = NULL
WHERE next_task_id IN (SELECT id FROM (
    SELECT id FROM housekeeping_tasks WHERE task_type = 'STAYOVER') AS stayover);

-- MySQL không cho UPDATE một bảng mà đọc chính nó trong truy vấn con — bọc thêm một lớp
-- bảng dẫn xuất để MySQL tạo bảng tạm.
UPDATE housekeeping_tasks
SET parent_task_id = NULL
WHERE parent_task_id IN (SELECT id FROM (
    SELECT id FROM housekeeping_tasks WHERE task_type = 'STAYOVER') AS stayover);

DELETE FROM housekeeping_tasks WHERE task_type = 'STAYOVER';

-- Bộ giá trị còn lại sau khi bỏ STAYOVER. Lý do hủy "khách trả phòng" (BR-HK-10) và "quản lý hủy"
-- (chỉ hủy được việc dọn hằng ngày — Q13) không còn đường nào sinh ra.
ALTER TABLE housekeeping_tasks DROP CHECK ck_hk_stayover_no_inspection;
ALTER TABLE housekeeping_tasks DROP CHECK ck_hk_task_type;
ALTER TABLE housekeeping_tasks DROP CHECK ck_hk_created_source;
ALTER TABLE housekeeping_tasks DROP CHECK ck_hk_cancel_reason;

-- Cột task_type giữ lại (chỉ còn CHECKOUT): cột sinh open_task_key của BR-HK-11 dựa vào nó.
ALTER TABLE housekeeping_tasks
    MODIFY COLUMN created_source VARCHAR(30) NOT NULL COMMENT 'CHECKOUT_AUTO | INSPECTION_FAILED — BR-HK-12',
    MODIFY COLUMN cancel_reason VARCHAR(30) NULL COMMENT 'Lý do HỦY task — BR-HK-09 (phòng Không khả dụng)';

ALTER TABLE housekeeping_tasks
    ADD CONSTRAINT ck_hk_task_type CHECK (task_type = 'CHECKOUT'),
    ADD CONSTRAINT ck_hk_created_source CHECK (created_source IN ('CHECKOUT_AUTO', 'INSPECTION_FAILED')),
    ADD CONSTRAINT ck_hk_cancel_reason CHECK (cancel_reason IS NULL OR cancel_reason = 'ROOM_UNAVAILABLE');

-- ── 2) Một phòng nhiều người dọn ─────────────────────────────────────────────
CREATE TABLE housekeeping_task_assignees (
    task_id   CHAR(36) NOT NULL,
    staff_id  CHAR(36) NOT NULL,
    CONSTRAINT pk_hk_assignees PRIMARY KEY (task_id, staff_id),
    CONSTRAINT fk_hk_assignees_task  FOREIGN KEY (task_id)  REFERENCES housekeeping_tasks (id) ON DELETE CASCADE,
    -- Không CASCADE: xóa vĩnh viễn tài khoản chỉ được khi người đó chưa phát sinh dữ liệu
    -- (UserService.deleteUserPermanently dựa vào khóa ngoại) — từng dọn phòng là đã phát sinh.
    CONSTRAINT fk_hk_assignees_staff FOREIGN KEY (staff_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_hk_assignees_staff ON housekeeping_task_assignees (staff_id);

-- Giữ nguyên người đã dọn của mọi việc cũ, kể cả việc đã xong (lịch sử ai dọn phòng nào).
INSERT INTO housekeeping_task_assignees (task_id, staff_id)
SELECT id, assigned_staff_id
FROM housekeeping_tasks
WHERE assigned_staff_id IS NOT NULL;

-- Ràng buộc "chưa phân công thì không có người" nằm trên cột cũ; giờ service tự giữ bất biến đó.
ALTER TABLE housekeeping_tasks DROP CHECK ck_hk_assignment_pair;
ALTER TABLE housekeeping_tasks DROP FOREIGN KEY fk_hk_staff;
DROP INDEX idx_hk_staff_date ON housekeeping_tasks;
ALTER TABLE housekeeping_tasks DROP COLUMN assigned_staff_id;

CREATE INDEX idx_hk_location_date ON housekeeping_tasks (location_id, assigned_date);
