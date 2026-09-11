ALTER TABLE users
    MODIFY COLUMN role ENUM(
        'ADMIN_PLATFORM',
        'DIRECTOR',
        'MANAGER',
        'RECEPTIONIST',
        'CLEANER'
    ) NOT NULL;