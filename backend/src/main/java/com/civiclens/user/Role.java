package com.civiclens.user;

public enum Role {
    CITIZEN,
    DEPARTMENT_ADMIN,
    SYSTEM_ADMIN;

    public boolean isOfficial() {
        return this == DEPARTMENT_ADMIN || this == SYSTEM_ADMIN;
    }
}
