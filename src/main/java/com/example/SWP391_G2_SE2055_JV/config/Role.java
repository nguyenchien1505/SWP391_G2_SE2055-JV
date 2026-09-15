package com.example.SWP391_G2_SE2055_JV.config;

/**
 * System roles — Milestone 1
 *
 * ADMIN_PLATFORM : manages the SaaS platform (hotels, subscriptions) DIRECTOR :
 * hotel director — sets high-level policy (e.g. work-schedule policy) and has
 * read-only oversight of day-to-day operations otherwise MANAGER : hotel operations
 * manager — full hotel management RECEPTIONIST : front desk — room status,
 * check-in/out HOUSEKEEPING : housekeeping staff — cleaning schedule, room status
 * update
 */
public enum Role {
    ADMIN_PLATFORM,
    DIRECTOR,
    MANAGER,
    RECEPTIONIST,
    HOUSEKEEPING
}
