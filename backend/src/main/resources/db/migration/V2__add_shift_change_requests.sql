-- Adds shift_change_requests, which was listed in the ERD domain index but never had a
-- detailed entity, columns, or migration. Lets staff (Receptionist/Housekeeping) request a
-- change to an assigned shift and lets a Manager approve/reject it — the Work Schedule
-- Management counterpart to cleaning_assignments' completion workflow.

CREATE TABLE shift_change_requests (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    shift_id               BIGINT       NOT NULL,
    requested_by           BIGINT       NOT NULL,
    requested_date         DATE,
    requested_start_time   TIME,
    requested_end_time     TIME,
    reason                 VARCHAR(500),
    status                 VARCHAR(20),
    reviewed_by            BIGINT,
    reviewed_at            DATETIME,
    review_note            VARCHAR(500),
    created_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by             BIGINT,
    is_deleted             BIT(1)       NOT NULL DEFAULT b'0',
    deleted_at             DATETIME,
    deleted_by             BIGINT,
    CONSTRAINT fk_shift_change_requests_shift          FOREIGN KEY (shift_id)       REFERENCES shifts (id),
    CONSTRAINT fk_shift_change_requests_requested_by    FOREIGN KEY (requested_by)   REFERENCES users (id),
    CONSTRAINT fk_shift_change_requests_reviewed_by     FOREIGN KEY (reviewed_by)    REFERENCES users (id),
    CONSTRAINT fk_shift_change_requests_created_by      FOREIGN KEY (created_by)     REFERENCES users (id),
    CONSTRAINT fk_shift_change_requests_deleted_by      FOREIGN KEY (deleted_by)     REFERENCES users (id)
);
