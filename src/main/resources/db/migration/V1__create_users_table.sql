-- V1__create_users_table.sql
CREATE TABLE IF NOT EXISTS users (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    username             VARCHAR(60)  NOT NULL,
    password             VARCHAR(255) NOT NULL,
    email                VARCHAR(120) NOT NULL,
    role                 VARCHAR(30)  NOT NULL,
    enabled              TINYINT(1)   NOT NULL DEFAULT 1,
    must_change_password TINYINT(1)   NOT NULL DEFAULT 0,
    created_at           DATETIME(6),
    updated_at           DATETIME(6),

    CONSTRAINT pk_users          PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email    UNIQUE (email)
);

