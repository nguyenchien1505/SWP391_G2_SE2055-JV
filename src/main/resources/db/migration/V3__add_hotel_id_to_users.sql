-- Add hotelId for multi-tenant isolation
-- NULL for ADMIN_PLATFORM accounts
ALTER TABLE users
    ADD COLUMN hotel_id BIGINT NULL AFTER phone;