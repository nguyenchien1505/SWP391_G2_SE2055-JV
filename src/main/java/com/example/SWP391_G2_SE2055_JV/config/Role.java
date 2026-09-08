package com.example.SWP391_G2_SE2055_JV.config;

/**
 * System roles aligned to the hotel workforce actors.
 *
 * Hierarchy (widest → narrowest access):
 *   OWNER → MANAGER → DEPARTMENT_MANAGER → HR → SUPERVISOR → ACCOUNTANT → EMPLOYEE
 */
public enum Role {
    OWNER,
    MANAGER,
    DEPARTMENT_MANAGER,
    HR,
    SUPERVISOR,
    ACCOUNTANT,
    EMPLOYEE
}
