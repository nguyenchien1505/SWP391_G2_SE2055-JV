ALTER TABLE users
    MODIFY COLUMN role ENUM(
        'ADMIN_PLATFORM',
        'DIRECTOR',
        'MANAGER',
        'RECEPTIONIST',
        'HOUSEKEEPING'
    ) NOT NULL;